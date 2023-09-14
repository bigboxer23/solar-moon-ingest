package com.bigboxer23.solar_moon;

import com.bigboxer23.solar_moon.data.Device;
import com.bigboxer23.solar_moon.data.DeviceAttribute;
import com.bigboxer23.solar_moon.data.DeviceData;
import com.bigboxer23.solar_moon.web.TransactionUtil;
import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.xpath.*;
import okhttp3.Credentials;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Class to read data from the generation meter web interface */
@Component
public class GenerationMeterComponent implements MeterConstants {

	private static final Map<String, String> fields = new HashMap<>();

	static {
		fields.put(TOTAL_ENG_CONS, TOTAL_ENG_CONS);
		fields.put(TOTAL_REAL_POWER, TOTAL_REAL_POWER);
		fields.put(AVG_CURRENT, AVG_CURRENT);
		fields.put(AVG_VOLT, AVG_VOLT);
		fields.put(TOTAL_PF, TOTAL_PF);
		fields.put("Energy Consumption", TOTAL_ENG_CONS);
		fields.put("Real Power", TOTAL_REAL_POWER);
		fields.put("Current", AVG_CURRENT);
		fields.put("Voltage, Line to Neutral", AVG_VOLT);
		fields.put("Power Factor", TOTAL_PF);
		fields.put("kWh del+rec", TOTAL_ENG_CONS);
		fields.put("I a", AVG_CURRENT);
		fields.put("Vll ab", AVG_VOLT);
		fields.put("PF sign tot", TOTAL_PF);
	}

	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterComponent.class);

	private final OpenSearchComponent openSearch;

	private final AlarmComponent alarmComponent;

	private final Map<String, Float> deviceTotalEnergyConsumed = new HashMap<>();

	private final DeviceComponent deviceComponent;

	private final SiteComponent siteComponent;

	public GenerationMeterComponent(
			OpenSearchComponent openSearch,
			AlarmComponent alarmComponent,
			DeviceComponent deviceComponent,
			SiteComponent siteComponent) {
		this.openSearch = openSearch;
		this.alarmComponent = alarmComponent;
		this.deviceComponent = deviceComponent;
		this.siteComponent = siteComponent;
	}

	// @Scheduled(fixedDelay = 5000)
	@Scheduled(cron = "0 */15 * * * ?")
	private void fetchData() throws IOException {
		LocalDateTime fetchDate = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
		Date date = Date.from(fetchDate.atZone(ZoneId.systemDefault()).toInstant());
		logger.info("Pulling devices");
		List<DeviceData> deviceData = deviceComponent.getDevices(false).stream()
				.filter(device -> !device.isPushedDevice())
				.map(this::getDeviceInformation)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		if (!deviceData.isEmpty()) {
			openSearch.logData(date, deviceData);
			alarmComponent.fireAlarms(deviceData);
		}
		logger.info("end of fetch data");
	}

	public DeviceData getDeviceInformation(Device device) {
		if (device == null || device.isPushedDevice()) {
			logger.warn("device or user or pw is null, cannot fetch data: " + device);
			return null;
		}
		String body = "";
		try (Response response = OkHttpUtil.getSynchronous(
				device.getAddress(), getAuthCallback(device.getUser(), device.getPassword()))) {
			body = response.body().string();
			if (!response.isSuccessful()) {
				logger.warn("getDeviceInformation unable to fetch data: Response code: "
						+ response.code()
						+ "\nbody: "
						+ body);
				return null;
			}
			logger.debug("fetched data: " + body);
		} catch (IOException e) {
			logger.warn("getDeviceInformation", e);
			return null;
		}
		return parseDeviceInformation(body, device.getSite(), device.getName(), device.getClientId());
	}

	public boolean handleDeviceBody(String body, String customerId) throws XPathExpressionException {
		if (customerId == null || body == null || customerId.isBlank() || body.isBlank()) {
			logger.error("no customer id, not doing anything. " + TransactionUtil.getLoggingStatement());
			return false;
		}
		logger.debug("parsing device body: " + body);
		if (!isUpdateEvent(body)) {
			logger.debug("event is not a LOGFILEUPLOAD, doing nothing: " + TransactionUtil.getLoggingStatement());
			return false;
		}
		Device device = Optional.ofNullable(findDeviceName(body))
				.map(deviceName -> findDeviceFromDeviceName(customerId, deviceName))
				.orElse(null);

		DeviceData deviceData = Optional.ofNullable(device)
				.map(server -> parseDeviceInformation(body, server.getSite(), server.getName(), customerId))
				.filter(DeviceData::isValid)
				.orElse(null);
		if (deviceData == null) {
			logger.info("device was not valid, not handling: " + TransactionUtil.getLoggingStatement());
			return false;
		}
		openSearch.logData(
				deviceData.getDate() != null ? deviceData.getDate() : new Date(),
				Collections.singletonList(deviceData));
		// siteComponent.handleSite(device.getSite());
		return true;
	}

	public boolean isUpdateEvent(String body) throws XPathExpressionException {
		NodeList nodes = (NodeList) XPathFactory.newInstance()
				.newXPath()
				.compile(MODE_PATH)
				.evaluate(new InputSource(new StringReader(body)), XPathConstants.NODESET);
		return nodes.getLength() > 0 && FILE_DATA.equals(nodes.item(0).getTextContent());
	}

	public String findDeviceName(String body) throws XPathExpressionException {
		NodeList nodes = (NodeList) XPathFactory.newInstance()
				.newXPath()
				.compile(DEVICE_NAME_PATH)
				.evaluate(new InputSource(new StringReader(body)), XPathConstants.NODESET);
		return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
	}

	private Device findDeviceFromDeviceName(String customerId, String deviceName) {
		if (customerId == null || customerId.isBlank() || deviceName == null || deviceName.isBlank()) {
			logger.warn("customer id or device name is null, can't find");
			return null;
		}
		logger.debug("finding device from device name/customer id " + deviceName + " " + customerId);
		return deviceComponent.getDevices(customerId).stream()
				.filter(server -> deviceName.equals(server.getDeviceName()))
				.findAny()
				.orElseGet(() -> {
					logger.warn("could not find device name for " + deviceName);
					return null;
				});
	}

	protected DeviceData parseDeviceInformation(String body, String site, String name, String customerId) {
		try {
			logger.debug("parsing device info " + site + ":" + name + "\n" + body);
			InputSource xml = new InputSource(new StringReader(body));
			NodeList nodes = (NodeList)
					XPathFactory.newInstance().newXPath().compile(POINT_PATH).evaluate(xml, XPathConstants.NODESET);
			DeviceData aDeviceData = new DeviceData(site, name, customerId);
			for (int i = 0; i < nodes.getLength(); i++) {
				String attributeName =
						nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
				if (fields.containsKey(attributeName)) {
					try {
						float value = Float.parseFloat(nodes.item(i)
								.getAttributes()
								.getNamedItem("value")
								.getNodeValue());
						aDeviceData.addAttribute(new DeviceAttribute(
								fields.get(attributeName),
								nodes.item(i)
										.getAttributes()
										.getNamedItem("units")
										.getNodeValue(),
								value));
					} catch (NumberFormatException nfe) {
						logger.warn("bad value retrieved from xml " + attributeName + "\n" + body, nfe);
					}
				}
			}
			calculateTotalRealPower(aDeviceData);
			calculateTotalEnergyConsumed(aDeviceData);
			calculateTime(aDeviceData, body);
			return aDeviceData;
		} catch (XPathExpressionException e) {
			logger.error("parseDeviceInformation", e);
		}
		return null;
	}

	private void calculateTime(DeviceData deviceData, String body) throws XPathExpressionException {
		InputSource xml = new InputSource(new StringReader(body));
		NodeList nodes = (NodeList)
				XPathFactory.newInstance().newXPath().compile(DATE_PATH).evaluate(xml, XPathConstants.NODESET);
		if (nodes.getLength() > 0) {
			Node timeNode = nodes.item(0);
			if (timeNode.getTextContent() == null
					|| "NULL".equals(timeNode.getTextContent())
					|| timeNode.getTextContent().isEmpty()
					|| timeNode.getAttributes().getNamedItem(ZONE) == null) {
				return;
			}
			SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);
			try {
				deviceData.setDate(sdf.parse(timeNode.getTextContent()
						+ " "
						+ timeNode.getAttributes().getNamedItem(ZONE).getNodeValue()));
			} catch (ParseException e) {
				logger.warn("cannot parse date string: " + body, e);
			}
		}
	}

	private void calculateTotalRealPower(DeviceData deviceData) {
		if (deviceData.getTotalRealPower() != -1) {
			logger.debug("Value already exists, not calculating");
			return;
		}
		if (deviceData.getAverageVoltage() == -1
				|| deviceData.getAverageCurrent() == -1
				|| deviceData.getPowerFactor() == -1) {
			logger.info("missing required values to calculate real power "
					+ deviceData.getName()
					+ " "
					+ deviceData.getAverageVoltage()
					+ ","
					+ deviceData.getAverageCurrent()
					+ ","
					+ deviceData.getPowerFactor());
			return;
		}
		double rp = (deviceData.getAverageVoltage()
						* deviceData.getAverageCurrent()
						* Math.abs(deviceData.getPowerFactor() / 100)
						* Math.sqrt(3))
				/ 1000f;
		deviceData.setTotalRealPower(
				new BigDecimal(rp).setScale(1, RoundingMode.HALF_UP).floatValue());
	}

	/**
	 * Calculate the difference of power consumed since the last run. Add a new field with the
	 * difference
	 *
	 * @param serverName
	 * @param attr
	 */
	private void calculateTotalEnergyConsumed(DeviceData deviceData) {
		if (deviceData.getName() == null) {
			logger.info("Can't calc total energy w/o device name");
			return;
		}
		logger.debug("calculating total energy consumed. " + deviceData.getName());
		float totalEnergyConsumption = deviceData.getTotalEnergyConsumed();
		if (totalEnergyConsumption < 0) {
			return;
		}
		Float previousTotalEnergyConsumed = deviceTotalEnergyConsumed.computeIfAbsent(
				deviceData.getName(), name -> openSearch.getTotalEnergyConsumed(deviceData.getName()));
		if (previousTotalEnergyConsumed != null) {
			deviceData.setEnergyConsumed(totalEnergyConsumption - previousTotalEnergyConsumed);
		}
		deviceTotalEnergyConsumed.put(deviceData.getName(), totalEnergyConsumption);
	}

	private RequestBuilderCallback getAuthCallback(String user, String pass) {
		return builder -> builder.addHeader("Authorization", Credentials.basic(user, pass));
	}
}

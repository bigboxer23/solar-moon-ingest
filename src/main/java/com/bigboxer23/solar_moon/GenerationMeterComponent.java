package com.bigboxer23.solar_moon;

import com.bigboxer23.solar_moon.data.Device;
import com.bigboxer23.solar_moon.data.DeviceAttribute;
import com.bigboxer23.solar_moon.data.DeviceData;
import com.bigboxer23.solar_moon.data.Servers;
import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import com.squareup.moshi.Moshi;
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
import javax.xml.xpath.*;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Class to read data from the generation meter web interface */
@Component
public class GenerationMeterComponent implements MeterConstants {

	private static final int CONFIG_CACHE_TIME = 5 * 60 * 1000; // 5m
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

	private final Moshi moshi = new Moshi.Builder().build();

	private final ElasticComponent elastic;

	private final OpenSearchComponent openSearch;

	private Servers servers;

	private long serversLastMod = -1;

	private AlarmComponent alarmComponent;

	private Map<String, Float> deviceTotalEnergyConsumed = new HashMap<>();

	private String configServer;

	private DeviceComponent deviceComponent;

	public GenerationMeterComponent(
			OpenSearchComponent openSearch,
			@Qualifier("elasticComponent") ElasticComponent elastic,
			AlarmComponent alarmComponent,
			DeviceComponent deviceComponent,
			Environment env) {
		this.openSearch = openSearch;
		this.elastic = elastic;
		this.alarmComponent = alarmComponent;
		this.deviceComponent = deviceComponent;
		configServer = env.getProperty("config.server");
		loadConfig();
	}

	protected boolean loadConfig() {
		if (servers != null && serversLastMod + CONFIG_CACHE_TIME > System.currentTimeMillis()) {
			logger.debug("using cached config values");
			return false;
		}
		logger.info("fetching config from db");
		Servers tempServers = new Servers();
		tempServers.setServers(new ArrayList<>());
		tempServers.setSites(new ArrayList<>());
		deviceComponent.getDeviceTable().scan().items().forEach(device -> (device.isVirtual()
						? tempServers.getSites()
						: tempServers.getServers())
				.add(device));
		servers = tempServers;
		serversLastMod = System.currentTimeMillis();
		return true;
	}

	protected void resetLoadedConfig() {
		servers = null;
		serversLastMod = -1;
	}

	protected void sendXMLToConfigurationServer() {
		servers.getServers().forEach(this::callConfigDeviceAPI);
		servers.getSites().forEach(device -> {
			device.setVirtual(true);
			callConfigDeviceAPI(device);
		});
	}

	private void callConfigDeviceAPI(Device device) {
		try (Response response = OkHttpUtil.putSynchronous(
				configServer + "/device",
				RequestBody.create(moshi.adapter(Device.class).toJson(device), MediaType.parse("application/json")),
				null)) {
			if (!response.isSuccessful()) {
				logger.warn("response was not successful: " + response.message() + " " + response.code());
			}
		} catch (IOException e) {
			logger.warn("sendXMLToConfigurationServer " + device, e);
		}
	}

	// @Scheduled(fixedDelay = 5000)
	@Scheduled(cron = "0 */15 * * * ?")
	private void fetchData() throws IOException, XPathExpressionException {
		loadConfig();
		if (servers == null) {
			logger.warn("fetchData:servers not configured, not doing anything");
			return;
		}
		LocalDateTime fetchDate = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
		Date date = Date.from(fetchDate.atZone(ZoneId.systemDefault()).toInstant());
		logger.info("Pulling devices");
		List<DeviceData> aDeviceData = new ArrayList<>();
		for (Device server : servers.getServers()) {
			if (!server.isPushedDevice()) {
				aDeviceData.add(getDeviceInformation(server));
			}
		}
		openSearch.logData(date, aDeviceData);
		alarmComponent.fireAlarms(aDeviceData);
		logger.info("end of fetch data");
	}

	public DeviceData getDeviceInformation(Device server) throws XPathExpressionException, IOException {
		if (server == null || server.isPushedDevice()) {
			logger.warn("server or user or pw is null, cannot fetch data: " + server);
			return null;
		}
		String body = "";
		try (Response response = OkHttpUtil.getSynchronous(
				server.getAddress(), getAuthCallback(server.getUser(), server.getPassword()))) {
			body = response.body().string();
			logger.debug("fetched data: " + body);
		}
		return parseDeviceInformation(body, server.getSite(), server.getName());
	}

	public boolean handleDeviceBody(String body) throws XPathExpressionException {
		if (servers == null) {
			logger.error("servers not defined, not doing anything.");
			return false;
		}
		logger.debug("parsing device body: " + body);
		if (!isUpdateEvent(body)) {
			logger.info("event is not a LOGFILEUPLOAD, doing nothing");
			return false;
		}
		DeviceData aDeviceData = Optional.ofNullable(findDeviceName(body))
				.map(this::findDeviceFromDeviceName)
				.map(server -> parseDeviceInformation(body, server.getSite(), server.getName()))
				.filter(DeviceData::isValid)
				.orElse(null);
		if (aDeviceData == null) {
			logger.info("device was not valid, not handling");
			return false;
		}
		openSearch.logData(
				aDeviceData.getDate() != null ? aDeviceData.getDate() : new Date(),
				Collections.singletonList(aDeviceData));
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

	private Device findDeviceFromDeviceName(String deviceName) {
		if (servers == null || deviceName == null || deviceName.isBlank()) {
			logger.warn("server or device is null, can't find");
			return null;
		}
		logger.debug("finding server from device name " + deviceName);
		return servers.getServers().stream()
				.filter(server -> deviceName.equals(server.getDeviceName()))
				.findAny()
				.orElseGet(() -> {
					logger.warn("could not find server name for " + deviceName);
					return null;
				});
	}

	protected DeviceData parseDeviceInformation(String body, String site, String name) {
		try {
			logger.debug("parsing device info " + site + ":" + name + "\n" + body);
			InputSource xml = new InputSource(new StringReader(body));
			NodeList nodes = (NodeList)
					XPathFactory.newInstance().newXPath().compile(POINT_PATH).evaluate(xml, XPathConstants.NODESET);
			DeviceData aDeviceData = new DeviceData(site, name);
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

	protected Servers getServers() {
		return servers;
	}
}

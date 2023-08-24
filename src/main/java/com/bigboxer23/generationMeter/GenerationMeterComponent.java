package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.Device;
import com.bigboxer23.generationMeter.data.DeviceAttribute;
import com.bigboxer23.generationMeter.data.Server;
import com.bigboxer23.generationMeter.data.Servers;
import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import com.squareup.moshi.JsonEncodingException;
import com.squareup.moshi.Moshi;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.util.*;
import javax.xml.xpath.*;
import okhttp3.Credentials;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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

	private final Moshi moshi = new Moshi.Builder().build();

	private final ElasticComponent elastic;

	private final OpenSearchComponent openSearch;

	private Servers servers;

	private long serversLastMod = -1;

	private AlarmComponent alarmComponent;

	private Map<String, Float> deviceTotalEnergyConsumed = new HashMap<>();

	private SiteComponent siteComponent;

	private String configFile;

	public GenerationMeterComponent(
			OpenSearchComponent openSearch,
			@Qualifier("elasticComponent") ElasticComponent elastic,
			AlarmComponent alarmComponent,
			SiteComponent siteComponent,
			Environment env)
			throws IOException {
		this.openSearch = openSearch;
		this.elastic = elastic;
		this.alarmComponent = alarmComponent;
		this.siteComponent = siteComponent;
		configFile = env.getProperty("config.file");
		loadConfig();
	}

	protected boolean loadConfig() throws IOException {
		logger.debug("reading config file");
		File config = new File(System.getProperty("user.dir") + File.separator + configFile);
		if (!config.exists()) {
			logger.warn("no "
					+ (System.getProperty("user.dir") + File.separator + configFile)
					+ " file exists, not doing anything");
			resetLoadedConfig();
			return false;
		}
		if (servers == null || serversLastMod < config.lastModified()) {
			logger.info("Config changed, reading config from file");
			try {
				servers = moshi.adapter(Servers.class)
						.fromJson(FileUtils.readFileToString(config, Charset.defaultCharset())
								.trim());
				serversLastMod = config.lastModified();
			} catch (JsonEncodingException e) {
				logger.error("invalid json.\n\n" + FileUtils.readFileToString(config, Charset.defaultCharset()), e);
				resetLoadedConfig();
				return false;
			}
			return true;
		}
		return false;
	}

	protected void resetLoadedConfig() {
		servers = null;
		serversLastMod = -1;
	}

	// @Scheduled(fixedDelay = 5000)
	@Scheduled(cron = "${scheduler-time}")
	private void fetchData() throws IOException, XPathExpressionException {
		loadConfig();
		if (servers == null) {
			return;
		}
		Date fetchDate = new Date();
		logger.info("starting fetch of data");
		List<Device> devices = new ArrayList<>();
		for (Server server : servers.getServers()) {
			if (!server.isPushedDevice()) {
				devices.add(getDeviceInformation(server));
			}
		}
		siteComponent.fillInSites(servers.getSites(), devices, servers.getServers());
		openSearch.logData(fetchDate, devices);
		alarmComponent.fireAlarms(devices);
		logger.info("end of fetch data");
	}

	public Device getDeviceInformation(Server server) throws XPathExpressionException, IOException {
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
		Device device = Optional.ofNullable(findDeviceName(body))
				.map(this::findServerFromDeviceName)
				.map(server -> parseDeviceInformation(body, server.getSite(), server.getName()))
				.filter(Device::isValid)
				.orElse(null);
		if (device == null) {
			logger.info("device was not valid, not handling");
			return false;
		}
		openSearch.logData(new Date(), Collections.singletonList(device));
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

	private Server findServerFromDeviceName(String deviceName) {
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

	protected Device parseDeviceInformation(String body, String site, String name) {
		try {
			logger.debug("parsing device info " + site + ":" + name + "\n" + body);
			InputSource xml = new InputSource(new StringReader(body));
			NodeList nodes = (NodeList)
					XPathFactory.newInstance().newXPath().compile(POINT_PATH).evaluate(xml, XPathConstants.NODESET);
			Device device = new Device(site, name);
			for (int i = 0; i < nodes.getLength(); i++) {
				String attributeName =
						nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
				if (fields.containsKey(attributeName)) {
					try {
						float value = Float.parseFloat(nodes.item(i)
								.getAttributes()
								.getNamedItem("value")
								.getNodeValue());
						device.addAttribute(new DeviceAttribute(
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
			calculateTotalRealPower(device);
			calculateTotalEnergyConsumed(device);
			return device;
		} catch (XPathExpressionException e) {
			logger.error("parseDeviceInformation", e);
		}
		return null;
	}

	private void calculateTotalRealPower(Device device) {
		if (device.getTotalRealPower() != -1) {
			logger.debug("Value already exists, not calculating");
			return;
		}
		if (device.getAverageVoltage() == -1 || device.getAverageCurrent() == -1 || device.getPowerFactor() == -1) {
			logger.info("missing required values to calculate real power "
					+ device.getName()
					+ " "
					+ device.getAverageVoltage()
					+ ","
					+ device.getAverageCurrent()
					+ ","
					+ device.getPowerFactor());
			return;
		}
		double rp = (device.getAverageVoltage()
						* device.getAverageCurrent()
						* Math.abs(device.getPowerFactor() / 100)
						* Math.sqrt(3))
				/ 1000f;
		device.setTotalRealPower(
				new BigDecimal(rp).setScale(1, RoundingMode.HALF_UP).floatValue());
	}

	/**
	 * Calculate the difference of power consumed since the last run. Add a new field with the
	 * difference
	 *
	 * @param serverName
	 * @param attr
	 */
	private void calculateTotalEnergyConsumed(Device device) {
		if (device.getName() == null) {
			logger.info("Can't calc total energy w/o device name");
			return;
		}
		logger.debug("calculating total energy consumed. " + device.getName());
		float totalEnergyConsumption = device.getTotalEnergyConsumed();
		if (totalEnergyConsumption < 0) {
			return;
		}
		Float previousTotalEnergyConsumed = deviceTotalEnergyConsumed.computeIfAbsent(
				device.getName(), name -> openSearch.getTotalEnergyConsumed(device.getName()));
		if (previousTotalEnergyConsumed != null) {
			device.setEnergyConsumed(totalEnergyConsumption - previousTotalEnergyConsumed);
		}
		deviceTotalEnergyConsumed.put(device.getName(), totalEnergyConsumption);
	}

	private RequestBuilderCallback getAuthCallback(String user, String pass) {
		return builder -> builder.addHeader("Authorization", Credentials.basic(user, pass));
	}

	protected Servers getServers() {
		return servers;
	}
}

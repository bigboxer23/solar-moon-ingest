package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.Device;
import com.bigboxer23.generationMeter.data.DeviceAttribute;
import com.bigboxer23.generationMeter.data.Server;
import com.bigboxer23.generationMeter.data.Servers;
import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import com.squareup.moshi.Moshi;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
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
	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterComponent.class);

	private final Moshi moshi = new Moshi.Builder().build();

	private final ElasticComponent elastic;

	private final OpenSearchComponent openSearch;

	private Set<String> fields = new HashSet<>();

	private Servers servers;

	private long serversLastMod = -1;

	private AlarmComponent alarmComponent;

	private Map<String, Float> deviceTotalEnergyConsumed = new HashMap<>();

	public GenerationMeterComponent(
			OpenSearchComponent openSearch,
			@Qualifier("elasticComponent") ElasticComponent elastic,
			AlarmComponent alarmComponent,
			Environment env)
			throws IOException {
		this.openSearch = openSearch;
		this.elastic = elastic;
		this.alarmComponent = alarmComponent;
		String fieldString = env.getProperty("generation-meter-fields");
		if (fieldString != null) {
			fields = Arrays.stream(fieldString.split(","))
					.map(String::trim)
					.filter(field -> !field.isEmpty())
					.collect(Collectors.toSet());
		}
		loadConfig();
	}

	protected boolean loadConfig() throws IOException {
		logger.debug("reading config file");
		File config = new File(System.getProperty("user.dir") + File.separator + "servers.json");
		if (!config.exists()) {
			logger.warn("no "
					+ (System.getProperty("user.dir") + File.separator + "servers.json")
					+ " file exists, not doing anything");
			servers = null;
			serversLastMod = -1;
			return false;
		}
		if (servers == null || serversLastMod < config.lastModified()) {
			logger.info("Config changed, reading config from file");
			servers = moshi.adapter(Servers.class)
					.fromJson(FileUtils.readFileToString(config, Charset.defaultCharset())
							.trim());
			serversLastMod = config.lastModified();
			return true;
		}
		return false;
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
			devices.add(getDeviceInformation(server));
		}
		fillInVirtualDevices(devices);
		openSearch.logData(fetchDate, devices);
		alarmComponent.fireAlarms(devices);
		logger.info("end of fetch data");
	}

	public Device getDeviceInformation(Server server) throws XPathExpressionException, IOException {
		if (server == null || server.getUser() == null || server.getPassword() == null) {
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

	public void handleDeviceBody(String body) throws XPathExpressionException {
		if (servers == null) {
			logger.error("servers not defined, not doing anything.");
			return;
		}
		logger.debug("parsing device body: " + body);
		InputSource xml = new InputSource(new StringReader(body));
		NodeList nodes = (NodeList)
				XPathFactory.newInstance().newXPath().compile(MODE_PATH).evaluate(xml, XPathConstants.NODESET);
		if (nodes.getLength() > 0 && FILE_DATA.equals(nodes.item(0).getTextContent())) {
			xml = new InputSource(new StringReader(body));
			nodes = (NodeList) XPathFactory.newInstance()
					.newXPath()
					.compile(DEVICE_NAME_PATH)
					.evaluate(xml, XPathConstants.NODESET);
			if (nodes.getLength() > 0) {
				Optional.ofNullable(findServerFromDeviceName(nodes.item(0).getTextContent()))
						.map(server -> parseDeviceInformation(body, server.getSite(), server.getName()))
						.ifPresent(device -> openSearch.logData(new Date(), Collections.singletonList(device)));
			}
		}
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
				.orElse(null);
	}

	private Device parseDeviceInformation(String body, String site, String name) {
		try {
			logger.debug("parsing device info " + site + ":" + name + "\n" + body);
			InputSource xml = new InputSource(new StringReader(body));
			NodeList nodes = (NodeList)
					XPathFactory.newInstance().newXPath().compile(POINT_PATH).evaluate(xml, XPathConstants.NODESET);
			Device device = new Device(site, name);
			for (int i = 0; i < nodes.getLength(); i++) {
				String attributeName =
						nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
				if (fields.contains(attributeName)) {
					device.addAttribute(new DeviceAttribute(
							attributeName,
							nodes.item(i).getAttributes().getNamedItem("units").getNodeValue(),
							Float.parseFloat(nodes.item(i)
									.getAttributes()
									.getNamedItem("value")
									.getNodeValue())));
				}
			}
			if (device.getName() != null) {
				calculateTotalEnergyConsumed(device);
			}
			return device;
		} catch (XPathExpressionException e) {
			logger.error("parseDeviceInformation", e);
		}
		return null;
	}

	private void fillInVirtualDevices(List<Device> devices) {
		if (servers.getSites() == null) {
			return;
		}
		logger.debug("starting to fill in virtual devices");
		List<Device> sites = new ArrayList<>();
		servers.getSites().forEach(site -> {
			logger.debug("adding virtual device " + site.getSite());
			Device siteDevice = new Device(site.getName(), site.getName());
			siteDevice.setIsVirtual();
			sites.add(siteDevice);
			float totalEnergyConsumed = devices.stream()
					.filter(device -> device.getSite().equals(site.getName()))
					.map(Device::getEnergyConsumed)
					.filter(energy -> energy >= 0)
					.reduce(Float::sum)
					.orElse(-1f);
			if (totalEnergyConsumed > -1) {
				siteDevice.setEnergyConsumed(totalEnergyConsumed);
			}
			float totalRealPower = devices.stream()
					.filter(device -> device.getSite().equals(site.getName()))
					.map(Device::getTotalRealPower)
					.filter(energy -> energy >= 0)
					.reduce(Float::sum)
					.orElse(-1f);
			if (totalRealPower > -1) {
				siteDevice.setTotalRealPower(totalRealPower);
			}
		});
		devices.addAll(sites);
	}

	/**
	 * Calculate the difference of power consumed since the last run. Add a new field with the
	 * difference
	 *
	 * @param serverName
	 * @param attr
	 */
	private void calculateTotalEnergyConsumed(Device device) {
		logger.debug("calculating total energy consumed. " + device.getName());
		float totalEnergyConsumption = device.getTotalEnergyConsumed();
		if (totalEnergyConsumption < 0) {
			return;
		}
		Float previousTotalEnergyConsumed = deviceTotalEnergyConsumed.computeIfAbsent(
				device.getName(), name -> openSearch.getTotalEnergyConsumed(device));
		if (previousTotalEnergyConsumed != null) {
			device.setEnergyConsumed(totalEnergyConsumption - previousTotalEnergyConsumed);
		}
		deviceTotalEnergyConsumed.put(device.getName(), totalEnergyConsumption);
	}

	private RequestBuilderCallback getAuthCallback(String user, String pass) {
		return builder -> builder.addHeader("Authorization", Credentials.basic(user, pass));
	}
}

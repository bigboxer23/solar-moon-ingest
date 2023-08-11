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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Class to read data from the generation meter web interface */
@Component
public class GenerationMeterComponent {
	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterComponent.class);

	@Value("${generation-meter-user}")
	private String apiUser;

	@Value("${generation-meter-pass}")
	private String apiPass;

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
			Environment env) {
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
			String body = "";
			try (Response response = OkHttpUtil.getSynchronous(server.getAddress(), getAuthCallback())) {
				body = response.body().string();
				logger.debug("fetched data: " + body);
			}
			InputSource xml = new InputSource(new StringReader(body));
			NodeList nodes = (NodeList) XPathFactory.newInstance()
					.newXPath()
					.compile("/DAS/devices/device/records/record/point")
					.evaluate(xml, XPathConstants.NODESET);
			Device device = new Device(server.getSite(), server.getName());
			for (int i = 0; i < nodes.getLength(); i++) {
				String name = nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
				if (fields.contains(name)) {
					device.addAttribute(new DeviceAttribute(
							name,
							nodes.item(i).getAttributes().getNamedItem("units").getNodeValue(),
							Float.parseFloat(nodes.item(i)
									.getAttributes()
									.getNamedItem("value")
									.getNodeValue())));
				}
			}
			calculateTotalEnergyConsumed(device);
			devices.add(device);
		}
		fillInVirtualDevices(devices);
		openSearch.logData(fetchDate, devices);
		alarmComponent.fireAlarms(devices);
		logger.info("end of fetch data");
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

	private RequestBuilderCallback getAuthCallback() {
		return builder -> builder.addHeader("Authorization", Credentials.basic(apiUser, apiPass));
	}
}

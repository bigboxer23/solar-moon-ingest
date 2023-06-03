package com.bigboxer23.generationMeter;

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

	private Set<String> fields = new HashSet<>();

	private Servers servers;

	private Map<String, Float> deviceTotalEnergyConsumed = new HashMap<>();

	public GenerationMeterComponent(ElasticComponent elastic, Environment env) {
		this.elastic = elastic;
		String fieldString = env.getProperty("generation-meter-fields");
		if (fieldString != null) {
			fields = Arrays.stream(fieldString.split(","))
					.map(String::trim)
					.filter(field -> !field.isEmpty())
					.collect(Collectors.toSet());
		}
	}

	private boolean loadConfig() throws IOException {
		File config = new File(System.getProperty("user.dir") + File.separator + "servers.json");
		if (!config.exists()) {
			logger.warn("no servers.json file exists, not doing anything");
			return false;
		}
		servers = moshi.adapter(Servers.class)
				.fromJson(FileUtils.readFileToString(config, Charset.defaultCharset())
						.trim());
		return true;
	}

	// @Scheduled(fixedDelay = 50000)
	@Scheduled(cron = "${scheduler-time}")
	private void fetchData() throws IOException, XPathExpressionException {
		if (!loadConfig()) {
			return;
		}
		logger.info("starting fetch of data");
		for (Server server : servers.getServers()) {
			try (Response response = OkHttpUtil.getSynchronous(server.getAddress(), getAuthCallback())) {
				String body = response.body().string();
				logger.debug("fetched data: " + body);
				InputSource xml = new InputSource(new StringReader(body));
				NodeList nodes = (NodeList) XPathFactory.newInstance()
						.newXPath()
						.compile("/DAS/devices/device/records/record/point")
						.evaluate(xml, XPathConstants.NODESET);
				List<DeviceAttribute> attributes = new ArrayList<>();
				for (int i = 0; i < nodes.getLength(); i++) {
					String name =
							nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
					if (fields.contains(name)) {
						attributes.add(new DeviceAttribute(
								name,
								nodes.item(i)
										.getAttributes()
										.getNamedItem("units")
										.getNodeValue(),
								Float.parseFloat(nodes.item(i)
										.getAttributes()
										.getNamedItem("value")
										.getNodeValue())));
					}
				}
				attributes.add(new DeviceAttribute("site", "", server.getSite()));
				attributes.add(new DeviceAttribute("device-name", "", server.getName()));
				calculateTotalEnergyConsumed(server.getName(), attributes);
				logger.debug("sending to elastic component");
				elastic.logData(server.getName(), attributes);
				logger.info("end of fetch data");
			}
		}
	}

	/**
	 * Calculate the difference of power consumed since the last run. Add a new field with the
	 * difference
	 *
	 * @param serverName
	 * @param attr
	 */
	private void calculateTotalEnergyConsumed(String serverName, List<DeviceAttribute> attr) {
		logger.debug("calculating total energy consumed.");
		Optional<DeviceAttribute> totalEnergyConsumption = attr.stream()
				.filter(device -> device.getName().equals("Total Energy Consumption"))
				.findAny();
		if (totalEnergyConsumption.isEmpty()) {
			return;
		}
		Float previousTotalEnergyConsumed = deviceTotalEnergyConsumed.get(serverName);
		if (previousTotalEnergyConsumed != null) {
			float energyConsumed = (Float) totalEnergyConsumption.get().getValue() - previousTotalEnergyConsumed;
			attr.add(new DeviceAttribute(
					"energy-consumption", totalEnergyConsumption.get().getUnit(), energyConsumed));
		}
		deviceTotalEnergyConsumed.put(
				serverName, (Float) totalEnergyConsumption.get().getValue());
	}

	private RequestBuilderCallback getAuthCallback() {
		return builder -> builder.addHeader("Authorization", Credentials.basic(apiUser, apiPass));
	}
}
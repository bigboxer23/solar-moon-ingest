package com.bigboxer23.generationMeter;

import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.xpath.*;
import okhttp3.Credentials;
import okhttp3.Response;
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

	@Value("${generation-meter-name}")
	private String name;

	@Value("${generation-meter-site}")
	private String site;

	@Value("${generation-meter-url}")
	private String apiUrl;

	@Value("${generation-meter-user}")
	private String apiUser;

	@Value("${generation-meter-pass}")
	private String apiPass;

	private final ElasticComponent elastic;

	private Set<String> fields = new HashSet<>();

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

	// @Scheduled(fixedDelay = 50000)
	@Scheduled(cron = "${scheduler-time}")
	private void fetchData() throws IOException, XPathExpressionException {
		logger.info("starting fetch of data");
		try (Response response = OkHttpUtil.getSynchronous(apiUrl, getAuthCallback())) {
			String body = response.body().string();
			logger.debug("fetched data: " + body);
			InputSource xml = new InputSource(new StringReader(body));
			NodeList nodes = (NodeList) XPathFactory.newInstance()
					.newXPath()
					.compile("/DAS/devices/device/records/record/point")
					.evaluate(xml, XPathConstants.NODESET);
			List<DeviceAttribute> attributes = new ArrayList<>();
			for (int i = 0; i < nodes.getLength(); i++) {
				String name = nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
				if (fields.contains(name)) {
					attributes.add(new DeviceAttribute(
							name,
							nodes.item(i).getAttributes().getNamedItem("units").getNodeValue(),
							Float.parseFloat(nodes.item(i)
									.getAttributes()
									.getNamedItem("value")
									.getNodeValue())));
				}
			}
			attributes.add(new DeviceAttribute("site", "", site));
			attributes.add(new DeviceAttribute("device-name", "", name));
			logger.debug("sending to elastic component");
			elastic.logData(name, attributes);
			logger.info("end of fetch data");
		}
	}

	private RequestBuilderCallback getAuthCallback() {
		return builder -> builder.addHeader("Authorization", Credentials.basic(apiUser, apiPass));
	}
}

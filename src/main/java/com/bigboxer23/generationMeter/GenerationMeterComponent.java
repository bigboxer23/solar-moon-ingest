package com.bigboxer23.generationMeter;

import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import javax.xml.xpath.*;
import okhttp3.Credentials;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

	@Value("${generation-meter-url}")
	private String apiUrl;

	@Value("${generation-meter-user}")
	private String apiUser;

	@Value("${generation-meter-pass}")
	private String apiPass;

	private ElasticComponent elastic;

	public GenerationMeterComponent(ElasticComponent elastic) {
		this.elastic = elastic;
	}

	// @Scheduled(fixedDelay = 15000)
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
			Map<String, DeviceAttribute> attributes = new HashMap<>();
			for (int i = 0; i < nodes.getLength(); i++) {
				String name = nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
				attributes.put(
						name,
						new DeviceAttribute(
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
			logger.debug("sending to elastic component");
			elastic.logData(name, attributes);
			logger.info("end of fetch data");
		}
	}

	private RequestBuilderCallback getAuthCallback() {
		return builder -> builder.addHeader("Authorization", Credentials.basic(apiUser, apiPass));
	}
}

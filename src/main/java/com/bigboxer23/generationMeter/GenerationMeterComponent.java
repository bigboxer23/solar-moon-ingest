package com.bigboxer23.generationMeter;

import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import java.io.IOException;
import java.io.StringReader;
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

	@Value("${generation-meter-url}")
	private String apiUrl;

	@Value("${generation-meter-user}")
	private String apiUser;

	@Value("${generation-meter-pass}")
	private String apiPass;

	private ElasticComponent elastic;

	// @Scheduled(fixedDelay = 1000)
	@Scheduled(cron = "${scheduler-time}")
	private void fetchData() throws IOException, XPathExpressionException {
		logger.info("starting fetch of data");
		try (Response response = OkHttpUtil.getSynchronous(apiUrl, getAuthCallback())) {
			String body = response.body().string();
			logger.debug("fetched data: " + body);
			InputSource xml = new InputSource(new StringReader(body));
			XPath xpath = XPathFactory.newInstance().newXPath();
			String expression = "/DAS/devices/device/records/record/point";
			XPathExpression xPathExpression = xpath.compile(expression);

			// Evaluate the XPath expression and print the results
			NodeList nodes = (NodeList) xPathExpression.evaluate(xml, XPathConstants.NODESET);
			for (int i = 0; i < nodes.getLength(); i++) {
				logger.info(nodes.item(i).getAttributes().getNamedItem("name").getNodeValue());
				logger.info(nodes.item(i).getAttributes().getNamedItem("value").getNodeValue());
				logger.info(nodes.item(i).getAttributes().getNamedItem("units").getNodeValue());
			}
		}
	}

	private RequestBuilderCallback getAuthCallback() {
		return builder -> builder.addHeader("Authorization", Credentials.basic(apiUser, apiPass));
	}
}

package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.DeviceAttribute;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** */
@Component
public class ElasticComponent {

	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterComponent.class);

	@Value("${elastic.url}")
	private String elasticUrl;

	private RestHighLevelClient client;
	private static final String INDEX_NAME = "generation-meter";

	private static final String TYPE = "Status";

	public void logData(String deviceName, List<DeviceAttribute> devices) {
		logger.debug("logData");
		BulkRequest bulkRequest = new BulkRequest();
		Map<String, Object> document = new HashMap<>();
		devices.forEach((device) -> document.put(device.getName(), device.getValue()));
		document.put("@timestamp", new Date());
		bulkRequest.add(
				new IndexRequest(INDEX_NAME, TYPE, deviceName + ":" + System.currentTimeMillis()).source(document));
		if (bulkRequest.numberOfActions() > 0) {
			logger.debug("Sending Request to elastic");
			try {
				if (bulkRequest.numberOfActions() > 0) {
					logger.debug("Sending Request to elastic");
					getClient().bulk(bulkRequest);
				}
			} catch (IOException theE) {
				logger.error("logStatusEvent:", theE);
			}
		}
	}

	@PreDestroy
	public void destroy() throws IOException {
		if (client != null) {
			logger.debug("closing elastic client");
			client.close();
		}
	}

	private RestHighLevelClient getClient() {
		if (client == null) {
			client = new RestHighLevelClient(RestClient.builder(HttpHost.create(elasticUrl)));
		}
		return client;
	}
}

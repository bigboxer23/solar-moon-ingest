package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.DeviceData;
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

	protected static final Logger logger = LoggerFactory.getLogger(GenerationMeterComponent.class);

	@Value("${elastic.url}")
	private String elasticUrl;

	private RestHighLevelClient client;
	protected static final String INDEX_NAME = "generation-meter";

	private static final String TYPE = "Status";

	public void logData(Date fetchDate, List<DeviceData> deviceData) {
		logger.debug("sending to elastic component");
		BulkRequest bulkRequest = new BulkRequest();
		deviceData.forEach(device -> {
			Map<String, Object> document = new HashMap<>();
			device.getAttributes().forEach((name, attr) -> document.put(attr.getName(), attr.getValue()));
			document.put("@timestamp", fetchDate);
			bulkRequest.add(new IndexRequest(INDEX_NAME, TYPE, device.getName() + ":" + System.currentTimeMillis())
					.source(document));
		});
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

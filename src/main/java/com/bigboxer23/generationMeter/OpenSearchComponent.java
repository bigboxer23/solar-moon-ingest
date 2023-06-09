package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.Device;
import com.bigboxer23.generationMeter.data.DeviceAttribute;
import com.bigboxer23.generationMeter.data.OpenSearchDTO;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** */
@Component
public class OpenSearchComponent extends ElasticComponent {
	private OpenSearchClient client;

	@Value("${opensearch.url}")
	private String openSearchUrl;

	@Value("${opensearch.user}")
	private String user;

	@Value("${opensearch.pw}")
	private String pass;

	public void logData(Date fetchDate, List<Device> devices) {
		logger.debug("sending to opensearch component");
		BulkRequest.Builder bulkRequest = new BulkRequest.Builder().index(INDEX_NAME);
		devices.forEach(device -> {
			device.addAttribute(new DeviceAttribute("@timestamp", null, fetchDate));
			bulkRequest.operations(new BulkOperation.Builder()
					.index(new IndexOperation.Builder<OpenSearchDTO>()
							.id(device.getName() + ":" + System.currentTimeMillis())
							.document(new OpenSearchDTO(device))
							.build())
					.build());
		});
		logger.debug("Sending Request to open search");
		try {
			BulkResponse response = getClient().bulk(bulkRequest.build());
			if (response.errors()) {
				response.items().forEach(item -> logger.warn("error:" + item.error()));
			}
		} catch (IOException theE) {
			logger.error("logStatusEvent:", theE);
		}
	}

	private OpenSearchClient getClient() {
		if (client == null) {
			final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, pass));
			final RestClient restClient = RestClient.builder(HttpHost.create(openSearchUrl))
					.setHttpClientConfigCallback(
							httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
					.build();
			client = new OpenSearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
		}
		return client;
	}
}

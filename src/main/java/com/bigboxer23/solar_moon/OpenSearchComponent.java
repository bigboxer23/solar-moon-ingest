package com.bigboxer23.solar_moon;

import com.bigboxer23.solar_moon.data.DeviceAttribute;
import com.bigboxer23.solar_moon.data.DeviceData;
import com.bigboxer23.solar_moon.data.OpenSearchDTO;
import java.io.IOException;
import java.util.*;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.core.search.SourceFilter;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** */
@Component
public class OpenSearchComponent extends ElasticComponent {

	private static final String TIMESTAMP = "@timestamp";

	private OpenSearchClient client;

	@Value("${opensearch.url}")
	private String openSearchUrl;

	@Value("${opensearch.user}")
	private String user;

	@Value("${opensearch.pw}")
	private String pass;

	private boolean isTest = false;

	public OpenSearchComponent(Environment env) {
		isTest = Boolean.parseBoolean(env.getProperty("testing"));
	}

	public void logData(Date fetchDate, List<DeviceData> deviceData) {
		if (isTest) {
			logger.info("not running, test is active.");
			return;
		}
		logger.debug("sending to opensearch component");
		BulkRequest.Builder bulkRequest = new BulkRequest.Builder().index(INDEX_NAME);
		deviceData.forEach(device -> {
			device.addAttribute(new DeviceAttribute(TIMESTAMP, null, fetchDate));
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

	public Float getTotalEnergyConsumed(String deviceName) {
		try {
			SearchRequest request = new SearchRequest.Builder()
					.index(Collections.singletonList(INDEX_NAME))
					.query(QueryBuilders.matchPhrase()
							.field(MeterConstants.DEVICE_NAME)
							.query(deviceName)
							.build()
							._toQuery())
					.sort(new SortOptions.Builder()
							.field(new FieldSort.Builder()
									.field(TIMESTAMP)
									.order(SortOrder.Desc)
									.build())
							.build())
					.size(1)
					.source(new SourceConfig.Builder()
							.filter(new SourceFilter.Builder()
									.includes(Collections.singletonList(MeterConstants.TOTAL_ENG_CONS))
									.build())
							.build())
					.build();
			SearchResponse<Map> response = getClient().search(request, Map.class);
			if (response.hits().hits().isEmpty()) {
				logger.warn("couldn't find previous value for " + deviceName);
				return null;
			}
			Map<String, Object> fields = response.hits().hits().get(0).source();
			if (fields == null) {
				logger.warn("No fields associated with result for " + deviceName);
				return null;
			}
			return Optional.ofNullable((Double) fields.get(MeterConstants.TOTAL_ENG_CONS))
					.map(Double::floatValue)
					.orElseGet(() -> {
						logger.warn("Unexpected value type for " + deviceName);
						return null;
					});
		} catch (IOException e) {
			logger.error("getTotalEnergyConsumed:", e);
			return null;
		}
	}

	public DeviceData getLastDeviceEntry(String deviceName) {
		try {
			SearchRequest request = new SearchRequest.Builder()
					.index(Collections.singletonList(INDEX_NAME))
					.query(QueryBuilders.bool()
							.must(
									QueryBuilders.matchPhrase()
											.field(MeterConstants.DEVICE_NAME)
											.query(deviceName)
											.build()
											._toQuery(),
									QueryBuilders.range()
											.field(TIMESTAMP)
											.gte(JsonData.of("now-15m"))
											.build()
											._toQuery())
							.build()
							._toQuery())
					.sort(new SortOptions.Builder()
							.field(new FieldSort.Builder()
									.field(TIMESTAMP)
									.order(SortOrder.Desc)
									.build())
							.build())
					.size(1)
					.build();
			SearchResponse<Map> response = getClient().search(request, Map.class);
			if (response.hits().hits().isEmpty()) {
				logger.debug("couldn't find previous value for " + deviceName);
				return null;
			}
			Map<String, Object> fields = response.hits().hits().get(0).source();
			if (fields == null) {
				logger.warn("No fields associated with result for " + deviceName);
				return null;
			}
			return new DeviceData(fields);
		} catch (IOException e) {
			logger.error("getLastDeviceEntry:", e);
			return null;
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

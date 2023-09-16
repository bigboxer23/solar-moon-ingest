package com.bigboxer23.solar_moon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bigboxer23.utils.http.OkHttpUtil;
import com.bigboxer23.utils.http.RequestBuilderCallback;
import java.io.IOException;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("/application-test.properties")
public class TestGenerationMeterController implements TestConstants, MeterConstants {
	@Value("${testingURL}")
	private String testURL;

	@Value("${testingpw}")
	private String testPW;

	@Test
	public void testuploadXmlContent() throws IOException {
		runAPItest(getRequestBody(device2Xml), getAuthCallback(testPW + "blah"), XML_FAILURE_RESPONSE);
		runAPItest(getRequestBody(device2Xml), null, XML_FAILURE_RESPONSE);
		runAPItest(getRequestBody(device2XmlNull), getAuthCallback(testPW), XML_FAILURE_RESPONSE);
		runAPItest(null, getAuthCallback(testPW), XML_FAILURE_RESPONSE);
		runAPItest(getRequestBody(device2Xml), getAuthCallback(testPW), XML_SUCCESS_RESPONSE);
	}

	private void runAPItest(RequestBody requestBody, RequestBuilderCallback authCallback, String XMLResponse)
			throws IOException {
		try (Response response = OkHttpUtil.postSynchronous(testURL + "/upload", requestBody, authCallback)) {
			assertEquals(XMLResponse, response.body().string());
		}
	}

	private RequestBuilderCallback getAuthCallback(String pass) {
		return builder -> builder.addHeader("Authorization", Credentials.basic("user", pass));
	}

	private RequestBody getRequestBody(String deviceXML) {
		return RequestBody.create(
				TestUtils.getDeviceXML(deviceXML, TestDeviceComponent.deviceName + 0, TimeUtils.get15mRoundedDate()),
				MediaType.parse("application/xml; charset=utf-8"));
	}
}

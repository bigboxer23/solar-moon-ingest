package com.bigboxer23.solar_moon;

import static org.junit.jupiter.api.Assertions.*;

import com.bigboxer23.solar_moon.open_search.OpenSearchComponent;
import com.bigboxer23.solar_moon.open_search.OpenSearchUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestSiteComponent {
	@Autowired
	private GenerationMeterComponent generationComponent;

	@Autowired
	private DeviceComponent deviceComponent;

	@Autowired
	private OpenSearchComponent openComponent;

	@Test
	public void testHandleSite() throws XPathExpressionException, InterruptedException {
		TestUtils.setupSite(deviceComponent);
		openComponent.deleteByCustomerId(TestDeviceComponent.clientId);
		Date date = TimeUtils.get15mRoundedDate();
		for (int ai = 0; ai < 4; ai++) {
			generationComponent.handleDeviceBody(
					TestUtils.getDeviceXML(TestDeviceComponent.deviceName + ai, date), TestDeviceComponent.clientId);
		}
		OpenSearchUtils.waitForIndexing();
		assertNull(openComponent.getLastDeviceEntry(TestDeviceComponent.clientId, TestDeviceComponent.SITE));
		generationComponent.handleDeviceBody(
				TestUtils.getDeviceXML(TestDeviceComponent.deviceName + 4, date), TestDeviceComponent.clientId);
		OpenSearchUtils.waitForIndexing();
		TestUtils.validateDateData(openComponent, TestDeviceComponent.SITE, date);
	}

	@Test
	public void testHandleSiteInterleaved() throws XPathExpressionException, InterruptedException {
		TestUtils.setupSite(deviceComponent);
		openComponent.deleteByCustomerId(TestDeviceComponent.clientId);
		Date date = TimeUtils.get15mRoundedDate();
		LocalDateTime ldt = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
		Date past =
				Date.from(ldt.minusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
		Date future =
				Date.from(ldt.plusMinutes(15).atZone(ZoneId.systemDefault()).toInstant());
		for (int ai = 0; ai < 4; ai++) {
			generationComponent.handleDeviceBody(
					TestUtils.getDeviceXML(TestDeviceComponent.deviceName + ai, date), TestDeviceComponent.clientId);
		}
		for (int ai = 0; ai < 5; ai++) {
			generationComponent.handleDeviceBody(
					TestUtils.getDeviceXML(TestDeviceComponent.deviceName + ai, past), TestDeviceComponent.clientId);
		}
		OpenSearchUtils.waitForIndexing();
		TestUtils.validateDateData(openComponent, TestDeviceComponent.SITE, past);
		assertNull(openComponent.getDeviceByTimePeriod(TestDeviceComponent.clientId, TestDeviceComponent.SITE, date));

		for (int ai = 0; ai < 5; ai++) {
			generationComponent.handleDeviceBody(
					TestUtils.getDeviceXML(TestDeviceComponent.deviceName + ai, future), TestDeviceComponent.clientId);
		}
		OpenSearchUtils.waitForIndexing();
		TestUtils.validateDateData(openComponent, TestDeviceComponent.SITE, future);
		assertNull(openComponent.getDeviceByTimePeriod(TestDeviceComponent.clientId, TestDeviceComponent.SITE, date));
		generationComponent.handleDeviceBody(
				TestUtils.getDeviceXML(TestDeviceComponent.deviceName + 4, date), TestDeviceComponent.clientId);
		OpenSearchUtils.waitForIndexing();
		TestUtils.validateDateData(openComponent, TestDeviceComponent.SITE, date);
	}
}

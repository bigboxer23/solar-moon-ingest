package com.bigboxer23.solar_moon;

import static org.junit.jupiter.api.Assertions.*;

import com.bigboxer23.solar_moon.data.Device;
import com.bigboxer23.solar_moon.data.DeviceData;
import java.text.SimpleDateFormat;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("/application-test.properties")
public class TestGenerationMeterComponent implements TestConstants {
	@Autowired
	private GenerationMeterComponent component;

	@Test
	public void testLoadConfig() {
		assertFalse(component.loadConfig());
		component.resetLoadedConfig();
		assertTrue(component.loadConfig());
		assertFalse(component.loadConfig());
	}

	@Test
	public void testFindDeviceName() throws XPathExpressionException {
		assertEquals(device1Name, component.findDeviceName(device1Xml));
		try {
			component.findDeviceName("invalid xml");
		} catch (XPathExpressionException e) {
			return;
		}
		fail();
	}

	@Test
	public void testIsUpdateEvent() throws XPathExpressionException {
		assertTrue(component.isUpdateEvent(device1Xml));
		assertFalse(component.isUpdateEvent(nonUpdateStatus));
		try {
			component.isUpdateEvent("invalid xml");
		} catch (XPathExpressionException e) {
			return;
		}
		fail();
	}

	@Test
	public void testCalculatedTotalRealPower() {
		DeviceData aDeviceData2 = component.parseDeviceInformation(device2Xml, "site1", TestDeviceComponent.deviceName);
		assertEquals(aDeviceData2.getTotalRealPower(), 422.7f);
		aDeviceData2.setPowerFactor(-aDeviceData2.getPowerFactor());
		assertEquals(aDeviceData2.getTotalRealPower(), 422.7f);
	}

	@Test
	public void testParseDeviceInformation() {
		DeviceData aDeviceData =
				component.parseDeviceInformation(device2XmlNull, "site1", TestDeviceComponent.deviceName);
		assertNotNull(aDeviceData);
		assertFalse(aDeviceData.isValid());
		aDeviceData = component.parseDeviceInformation(device2Xml, "site1", TestDeviceComponent.deviceName);
		assertNotNull(aDeviceData);
		assertTrue(aDeviceData.isValid());
		assertNull(component.parseDeviceInformation(
				TestDeviceComponent.deviceName, TestDeviceComponent.deviceName, TestDeviceComponent.deviceName));
	}

	@Test
	public void testHandleDeviceBody() throws XPathExpressionException {
		component.resetLoadedConfig();
		assertFalse(component.handleDeviceBody(device2Xml, null));
		component.loadConfig();
		assertFalse(component.handleDeviceBody(nonUpdateStatus, null));
		assertFalse(component.handleDeviceBody(device2XmlNull, null));
		assertTrue(component.handleDeviceBody(device2Xml, null));
		Device server = new Device();
		server.setName(TestDeviceComponent.deviceName);
		server.setDeviceName(TestDeviceComponent.deviceName);
		component.getServers().getServers().add(server);
		assertFalse(component.handleDeviceBody(device2XmlNull, null));
		assertTrue(component.handleDeviceBody(device2Xml, null));
		assertTrue(component.handleDeviceBody(device2Xml, TestDeviceComponent.clientId));
	}

	@Test
	public void testDateRead() {
		DeviceData aDeviceData = component.parseDeviceInformation(device2Xml, "site1", TestDeviceComponent.deviceName);
		assertNotNull(aDeviceData.getDate());
		SimpleDateFormat sdf = new SimpleDateFormat(MeterConstants.DATE_PATTERN);
		assertEquals(sdf.format(aDeviceData.getDate()), "2020-08-21 12:30:00 CDT");
		aDeviceData = component.parseDeviceInformation(device2XmlNoDate, "site1", TestDeviceComponent.deviceName);
		assertNull(aDeviceData.getDate());
		aDeviceData = component.parseDeviceInformation(device2XmlBadDate, "site1", TestDeviceComponent.deviceName);
		assertNull(aDeviceData.getDate());
		aDeviceData = component.parseDeviceInformation(device2XmlNoTZ, "site1", TestDeviceComponent.deviceName);
		assertNull(aDeviceData.getDate());
	}
}

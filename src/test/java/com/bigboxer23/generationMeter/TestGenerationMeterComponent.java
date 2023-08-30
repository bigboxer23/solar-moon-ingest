package com.bigboxer23.generationMeter;

import static org.junit.jupiter.api.Assertions.*;

import com.bigboxer23.generationMeter.data.DeviceData;
import com.bigboxer23.generationMeter.data.Server;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("/application-test.properties")
public class TestGenerationMeterComponent implements TestConstants {
	@Autowired
	private GenerationMeterComponent component;

	@Value("${config.file}")
	private String configFileName;

	@Test
	public void testLoadConfig() throws IOException {
		assertFalse(component.loadConfig());
		// test modification
		File file = new File(System.getProperty("user.dir") + File.separator + configFileName);
		long timeMillis = System.currentTimeMillis();
		FileTime accessFileTime = FileTime.fromMillis(timeMillis);
		Files.setAttribute(file.toPath(), "lastAccessTime", accessFileTime);
		file.setLastModified(timeMillis);
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
		DeviceData aDeviceData2 = component.parseDeviceInformation(device2Xml, "site1", device2Name);
		assertEquals(aDeviceData2.getTotalRealPower(), 422.7f);
		aDeviceData2.setPowerFactor(-aDeviceData2.getPowerFactor());
		assertEquals(aDeviceData2.getTotalRealPower(), 422.7f);
	}

	@Test
	public void testParseDeviceInformation() {
		DeviceData aDeviceData = component.parseDeviceInformation(device2XmlNull, "site1", device2Name);
		assertNotNull(aDeviceData);
		assertFalse(aDeviceData.isValid());
		aDeviceData = component.parseDeviceInformation(device2Xml, "site1", device2Name);
		assertNotNull(aDeviceData);
		assertTrue(aDeviceData.isValid());
		assertNull(component.parseDeviceInformation(device2Name, device2Name, device2Name));
	}

	@Test
	public void testHandleDeviceBody() throws XPathExpressionException, IOException {
		component.resetLoadedConfig();
		assertFalse(component.handleDeviceBody(device2Xml));
		component.loadConfig();
		assertFalse(component.handleDeviceBody(nonUpdateStatus));
		assertFalse(component.handleDeviceBody(device2XmlNull));
		assertFalse(component.handleDeviceBody(device2Xml));
		Server server = new Server();
		server.setName(device2Name);
		server.setDeviceName(device2Name);
		component.getServers().getServers().add(server);
		assertFalse(component.handleDeviceBody(device2XmlNull));
		assertTrue(component.handleDeviceBody(device2Xml));
	}

	@Test
	public void testDateRead() {
		DeviceData aDeviceData = component.parseDeviceInformation(device2Xml, "site1", device2Name);
		assertNotNull(aDeviceData.getDate());
		SimpleDateFormat sdf = new SimpleDateFormat(MeterConstants.DATE_PATTERN);
		assertEquals(sdf.format(aDeviceData.getDate()), "2020-08-21 12:30:00 CDT");
		aDeviceData = component.parseDeviceInformation(device2XmlNoDate, "site1", device2Name);
		assertNull(aDeviceData.getDate());
		aDeviceData = component.parseDeviceInformation(device2XmlBadDate, "site1", device2Name);
		assertNull(aDeviceData.getDate());
		aDeviceData = component.parseDeviceInformation(device2XmlNoTZ, "site1", device2Name);
		assertNull(aDeviceData.getDate());
	}
}

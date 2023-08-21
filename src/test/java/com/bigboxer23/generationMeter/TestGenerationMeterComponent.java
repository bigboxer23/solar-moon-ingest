package com.bigboxer23.generationMeter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestGenerationMeterComponent implements TestConstants {
	@Autowired
	private GenerationMeterComponent component;

	@Test
	public void testLoadConfig() throws IOException {
		assertFalse(component.loadConfig());
		// test modification
		File file = new File(System.getProperty("user.dir") + File.separator + "servers.json");
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
		try {
			component.isUpdateEvent("invalid xml");
		} catch (XPathExpressionException e) {
			return;
		}
		fail();
	}
}

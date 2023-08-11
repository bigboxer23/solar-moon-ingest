package com.bigboxer23.generationMeter;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.bigboxer23.generationMeter.data.Device;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestOpenSearchComponent {
	@Autowired
	private OpenSearchComponent component;

	@Test
	public void testGetTotalEnergyConsumed() {
		// test invalid case
		Float consumed = component.getTotalEnergyConsumed(new Device("testSite", "testDevice"));
		assertNull(consumed);
	}
}

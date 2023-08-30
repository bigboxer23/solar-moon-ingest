package com.bigboxer23.solar_moon;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("/application-test.properties")
public class TestOpenSearchComponent {
	@Autowired
	private OpenSearchComponent component;

	@Test
	public void testGetTotalEnergyConsumed() {
		// test invalid case
		Float consumed = component.getTotalEnergyConsumed("testDevice");
		assertNull(consumed);
	}
}

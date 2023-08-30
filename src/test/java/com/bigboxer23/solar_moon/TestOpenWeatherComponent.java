package com.bigboxer23.solar_moon;

import static org.junit.jupiter.api.Assertions.*;

import com.bigboxer23.solar_moon.data.Location;
import com.bigboxer23.solar_moon.data.WeatherSystemData;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource("/application-test.properties")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestOpenWeatherComponent {

	@Autowired
	private OpenWeatherComponent component;

	@Test
	@Order(1)
	public void testGetLatLongFromCity() {
		Location location = component.getLatLongFromCity("golden valley", "mn", 840);
		assertNotNull(location);
		assertEquals(44.9861176, location.getLat());
		assertEquals(-93.3784618, location.getLon());
		assertFalse(location.isFromCache());
		location = component.getLatLongFromCity("golden valley", "mn", 840);
		assertTrue(location.isFromCache());
	}

	@Test
	public void testGetSunriseSunsetFromCityStateCountry() {
		WeatherSystemData data = component.getSunriseSunsetFromCityStateCountry("golden valley", "mn", 581);
		assertNotNull(data);
		assertTrue(data.getSunrise() > -1);
		assertTrue(data.getSunset() > -1);
		assertFalse(data.isFromCache());
		data = component.getSunriseSunsetFromCityStateCountry("golden valley", "mn", 581);
		assertTrue(data.isFromCache());
		// invalid case
		data = component.getSunriseSunsetFromCityStateCountry("golden valley2", "nv", 582);
		assertNull(data);
	}
}

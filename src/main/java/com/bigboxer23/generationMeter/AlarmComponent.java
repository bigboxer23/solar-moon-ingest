package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.Device;
import com.bigboxer23.generationMeter.data.Location;
import java.io.IOException;
import java.util.List;

import com.bigboxer23.generationMeter.data.WeatherSystemData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** */
@Component
public class AlarmComponent {

	private static final Logger logger = LoggerFactory.getLogger(AlarmComponent.class);

	private OpenWeatherComponent openWeatherComponent;

	public AlarmComponent(OpenWeatherComponent openWeatherComponent) {
		this.openWeatherComponent = openWeatherComponent;
	}

	public void fireAlarms(List<Device> devices) throws IOException {
		logger.debug("checking alarms");
		// TODO: criteria for actually firing
		Location location = openWeatherComponent.getLatLongFromCity("golden valley", "mn", 581);
		WeatherSystemData sunriseSunset = openWeatherComponent.getSunriseSunset(location.getLat(), location.getLon());
		logger.debug("sunrise/sunset " + sunriseSunset.getSunrise() + "," + sunriseSunset.getSunset());
	}
}

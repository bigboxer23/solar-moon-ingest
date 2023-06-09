package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.Location;
import com.bigboxer23.generationMeter.data.WeatherData;
import com.bigboxer23.generationMeter.data.WeatherSystemData;
import com.bigboxer23.utils.http.OkHttpUtil;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** */
@Component
public class OpenWeatherComponent {
	private static final Logger logger = LoggerFactory.getLogger(OpenWeatherComponent.class);

	private static final String kOpenWeatherMapUrl =
			"https://api.openweathermap.org/data/2.5/weather?lat={0}&lon={1}&APPID={2}";

	public static final String kOpenWeatherMapCityToLatLong =
			"http://api.openweathermap.org/geo/1.0/direct?q={0}&limit=2&appid={1}";

	@Value("${openweathermap.api}")
	private String openWeatherMapAPIKey;

	private final Moshi moshi = new Moshi.Builder().build();

	public Location getLatLongFromCity(String city, String state, int countryCode) throws IOException {
		logger.info("retrieving lat/long from " + city + " " + state);
		try (Response response = OkHttpUtil.getSynchronous(
				MessageFormat.format(
						kOpenWeatherMapCityToLatLong, city + "," + state + "," + countryCode, openWeatherMapAPIKey),
				null)) {
			String body = response.body().string();
			logger.debug("lat/long body " + body);
			JsonAdapter<List<Location>> jsonAdapter =
					moshi.adapter(Types.newParameterizedType(List.class, Location.class));
			return Optional.ofNullable(jsonAdapter.fromJson(body))
					.map(loc -> loc.isEmpty() ? null : loc.get(0))
					.orElse(null);
		}
	}

	public WeatherSystemData getSunriseSunset(double latitude, double longitude) throws IOException {
		logger.debug("Fetching OpenWeatherMap data");
		try (Response response = OkHttpUtil.getSynchronous(
				MessageFormat.format(kOpenWeatherMapUrl, latitude, longitude, openWeatherMapAPIKey), null)) {
			String body = response.body().string();
			logger.debug("weather body " + body);
			return Optional.ofNullable(moshi.adapter(WeatherData.class).fromJson(body))
					.map(WeatherData::getSys)
					.orElse(null);
		}
	}
}

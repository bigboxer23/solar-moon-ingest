package com.bigboxer23.generationMeter;

import com.bigboxer23.generationMeter.data.Server;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import javax.xml.xpath.XPathExpressionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** */
@RestController
@Tag(name = "Generation Meter Controller", description = "Various APIs available for interacting with the meters.")
public class GenerationMeterController {

	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterController.class);

	private GenerationMeterComponent component;

	public GenerationMeterController(GenerationMeterComponent theComponent) {
		component = theComponent;
	}

	@GetMapping(value = "/validateDeviceInformation", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "Test whether the information sent can log into a valid meter",
			description = "Test the device connection information to see if can get valid data back")
	@ApiResponses({
		@ApiResponse(responseCode = HttpURLConnection.HTTP_BAD_REQUEST + "", description = "Bad request"),
		@ApiResponse(responseCode = HttpURLConnection.HTTP_OK + "", description = "success")
	})
	@Parameters({
		@Parameter(
				name = "type",
				description = "A resource to temporarily display on the Meural",
				required = true,
				example = "test type"),
		@Parameter(
				name = "url",
				description = "url to connect to the device",
				required = true,
				example = "https://deviceIP:devicePort"),
		@Parameter(name = "user", description = "username to access the device", required = true, example = "admin"),
		@Parameter(name = "pw", description = "password to access the device", required = true, example = "MyPassword")
	})
	public boolean validateDeviceInformation(
			String type, String url, String user, String pw, HttpServletResponse servletResponse) {
		try {
			Server server = new Server();
			server.setUser(user);
			server.setPassword(pw);
			server.setAddress(url);
			server.setType(type);
			return component.getDeviceInformation(server) != null;
		} catch (XPathExpressionException | IOException e) {
			logger.error("validateDeviceInformation:cannot get information for server: " + url, e);
		}
		return false;
	}
}

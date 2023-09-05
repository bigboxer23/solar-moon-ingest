package com.bigboxer23.solar_moon;

import com.bigboxer23.solar_moon.data.Device;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Base64;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** */
@RestController
@Tag(name = "Generation Meter Controller", description = "Various APIs available for interacting with the meters.")
public class GenerationMeterController implements MeterConstants {

	@Value("${uploadToken}")
	private String uploadToken;

	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterController.class);

	private GenerationMeterComponent component;

	private DeviceComponent deviceComponent;

	public GenerationMeterController(GenerationMeterComponent theComponent, DeviceComponent deviceComponent) {
		component = theComponent;
		this.deviceComponent = deviceComponent;
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
			Device server = new Device();
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

	@Operation(summary = "Trigger sending xml config content to configuration server.")
	@GetMapping(value = "sendXMLToConfigurationServer")
	public ResponseEntity<Boolean> sendXMLToConfigurationServer() {
		component.sendXMLToConfigurationServer();
		return new ResponseEntity<>(true, HttpStatus.OK);
	}

	@Operation(
			summary = "Endpoint to post xml content to body for parsing into device data",
			requestBody = @RequestBody(description = "XML string content to parse"))
	@PostMapping(value = "/upload")
	public ResponseEntity<String> uploadXmlContent(HttpServletRequest servletRequest) {
		logger.info("Received upload request: " + servletRequest.getHeader("X-Forwarded-For"));
		String deviceKeyOrUploadId = authenticateRequest(servletRequest);
		if (authenticateRequest(servletRequest) == null)
		{
			return new ResponseEntity<>("FAILURE", HttpStatus.UNAUTHORIZED);
		}
		try (BufferedReader reader = servletRequest.getReader()) {
			component.handleDeviceBody(IOUtils.toString(reader), isUploadToken(deviceKeyOrUploadId) ? null : deviceKeyOrUploadId);
		} catch (XPathExpressionException | IOException e) {
			logger.error("uploadXmlContent: " + servletRequest.getRemoteAddr(), e);
			return new ResponseEntity<>("FAILURE", HttpStatus.BAD_REQUEST);
		}
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.TEXT_XML);
		logger.debug("successfully uploaded data");
		return new ResponseEntity<>(XML_SUCCESS_RESPONSE, httpHeaders, HttpStatus.OK);
	}

	/**
	 * //TODO: remove uploadToken after migrating devices to device tokens
	 *
	 * @param servletRequest
	 * @return
	 */
	private String authenticateRequest(HttpServletRequest servletRequest) {
		String authorization = servletRequest.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Basic ")) {
			logger.warn("Missing authorization token.");
			return null;
		}
		String usernameAndPassword = authorization.substring(6);
		String decoded = new String(Base64.getDecoder().decode(usernameAndPassword));
		String[] parts = decoded.split(":");
		if (parts.length != 2) {
			logger.warn("Invalid auth, returning unauthorized: " + parts[0]);
			return null;
		}
		if (deviceComponent.findDeviceByDeviceKey(parts[1]) != null)
		{
			return parts[1];
		}
		if (isUploadToken(parts[1])) {
			return parts[1];
		}
		logger.warn("Invalid token, returning unauthorized: " + parts[1]);
		return null;
	}

	private boolean isUploadToken(String token)
	{
		return uploadToken.equals(token);
	}
}

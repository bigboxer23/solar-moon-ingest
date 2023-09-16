package com.bigboxer23.solar_moon;

import com.bigboxer23.solar_moon.data.Customer;
import com.bigboxer23.solar_moon.data.Device;
import com.bigboxer23.solar_moon.data.DeviceData;
import com.bigboxer23.solar_moon.web.Transaction;
import com.bigboxer23.solar_moon.web.TransactionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.Optional;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterController.class);

	private GenerationMeterComponent component;

	private CustomerComponent customerComponent;

	public GenerationMeterController(GenerationMeterComponent theComponent, CustomerComponent customerComponent) {
		component = theComponent;
		this.customerComponent = customerComponent;
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
	public ResponseEntity<Boolean> validateDeviceInformation(String type, String url, String user, String pw) {
		Device server = new Device();
		server.setUser(user);
		server.setPassword(pw);
		server.setAddress(url);
		server.setType(type);
		return new ResponseEntity<>(component.getDeviceInformation(server) != null, HttpStatus.OK);
	}

	@Transaction
	@Operation(
			summary = "Endpoint to post xml content to body for parsing into device data",
			requestBody = @RequestBody(description = "XML string content to parse"))
	@PostMapping(value = "/upload")
	public ResponseEntity<String> uploadXmlContent(HttpServletRequest servletRequest) {
		String customerId = authenticateRequest(servletRequest);
		if (customerId == null) {
			return new ResponseEntity<>("FAILURE", HttpStatus.UNAUTHORIZED);
		}
		try (BufferedReader reader = servletRequest.getReader()) {
			DeviceData data = component.handleDeviceBody(IOUtils.toString(reader), customerId);
			if (data == null) {
				return new ResponseEntity<>("FAILURE", HttpStatus.BAD_REQUEST);
			}
			HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.setContentType(MediaType.TEXT_XML);
			logger.info(TransactionUtil.getLoggingStatement() + "successfully uploaded data: " + data.getName());
			return new ResponseEntity<>(XML_SUCCESS_RESPONSE, httpHeaders, HttpStatus.OK);
		} catch (XPathExpressionException | IOException e) {
			logger.error(TransactionUtil.getLoggingStatement() + "uploadXmlContent:", e);
			return new ResponseEntity<>("FAILURE", HttpStatus.BAD_REQUEST);
		}
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
			logger.warn(TransactionUtil.getLoggingStatement() + "Missing authorization token.");
			return null;
		}
		String usernameAndPassword = authorization.substring(6);
		String decoded = new String(Base64.getDecoder().decode(usernameAndPassword));
		String[] parts = decoded.split(":");
		if (parts.length != 2) {
			logger.warn(TransactionUtil.getLoggingStatement() + "Invalid auth, returning unauthorized: " + parts[0]);
			return null;
		}
		String customerId = Optional.ofNullable(customerComponent.findCustomerIdByAccessKey(parts[1]))
				.map(Customer::getCustomerId)
				.orElse(null);
		if (customerId != null) {
			return customerId;
		}
		logger.warn(TransactionUtil.getLoggingStatement() + "Invalid token, returning unauthorized: " + parts[1]);
		return null;
	}
}

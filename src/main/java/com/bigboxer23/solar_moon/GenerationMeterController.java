package com.bigboxer23.solar_moon;

import com.bigboxer23.solar_moon.data.DeviceData;
import com.bigboxer23.solar_moon.web.AuthenticationUtils;
import com.bigboxer23.solar_moon.web.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** */
@RestController
@Tag(name = "Generation Meter Controller", description = "Various APIs available for interacting with the meters.")
public class GenerationMeterController implements MeterConstants, IComponentRegistry {
	private static final Logger logger = LoggerFactory.getLogger(GenerationMeterController.class);

	public GenerationMeterController() {}

	@Transaction
	@Operation(
			summary = "Endpoint to post xml content to body for parsing into device data",
			requestBody = @RequestBody(description = "XML string content to parse"))
	@PostMapping(value = "/upload")
	public ResponseEntity<String> uploadXmlContent(HttpServletRequest servletRequest) {
		String customerId =
				AuthenticationUtils.authenticateRequest(servletRequest.getHeader("Authorization"), customerComponent);
		if (customerId == null) {
			return new ResponseEntity<>(XML_FAILURE_RESPONSE, HttpStatus.UNAUTHORIZED);
		}
		try (BufferedReader reader = servletRequest.getReader()) {
			String body = IOUtils.toString(reader);
			HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.setContentType(MediaType.TEXT_XML);
			if (!generationComponent.isUpdateEvent(body)) {
				return new ResponseEntity<>(XML_SUCCESS_RESPONSE, httpHeaders, HttpStatus.OK);
			}
			DeviceData data = generationComponent.handleDeviceBody(body, customerId);
			if (data == null) {
				return new ResponseEntity<>(XML_FAILURE_RESPONSE, HttpStatus.BAD_REQUEST);
			}
			logger.info("successfully uploaded data: " + data.getName() + " : " + data.getDate());
			return new ResponseEntity<>(XML_SUCCESS_RESPONSE, httpHeaders, HttpStatus.OK);
		} catch (XPathExpressionException | IOException e) {
			logger.error("uploadXmlContent:", e);
			return new ResponseEntity<>(XML_FAILURE_RESPONSE, HttpStatus.BAD_REQUEST);
		}
	}
}

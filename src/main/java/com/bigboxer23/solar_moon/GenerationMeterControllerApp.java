package com.bigboxer23.solar_moon;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
		info =
				@Info(
						title = "Generation Meter Backend",
						version = "1",
						description = "Provides ability to interface w/various meters through api",
						contact =
								@Contact(
										name = "bigboxer23@gmail.com",
										url = "https://github.com/bigboxer23/Generation-Meter-To-Elastic")))
public class GenerationMeterControllerApp {
	public static void main(String[] args) {
		SpringApplication.run(GenerationMeterControllerApp.class, args);
	}
}

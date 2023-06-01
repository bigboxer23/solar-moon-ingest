package com.bigboxer23.generationMeter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GenerationMeterControllerApp {
	public static void main(String[] args) {
		SpringApplication.run(GenerationMeterControllerApp.class, args);
	}
}

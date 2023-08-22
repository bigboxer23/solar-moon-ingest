package com.bigboxer23.generationMeter;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Controller to redirect swagger urls */
@Controller
@Hidden
public class SwaggerController {

	/** Catch swagger requests and redirect */
	@GetMapping({"/swagger", "/swagger/"})
	public String swagger() {
		return "redirect:/swagger-ui.html";
	}
}
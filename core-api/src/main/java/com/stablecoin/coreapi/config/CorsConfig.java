package com.stablecoin.coreapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the portal call the API from the browser. The portal runs on another
 * origin (Vite on http://localhost:5173), so without this the browser blocks
 * its requests.
 *
 * The mapping is relative to the context path, so "/**" covers every endpoint
 * under /api.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Value("${app.cors.allowedOrigins}")
	private String[] allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "OPTIONS")
				// Idempotency-Key is a custom header, so the preflight must allow it
				.allowedHeaders("Content-Type", "Idempotency-Key")
				.maxAge(3600);
	}
}

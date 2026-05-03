package com.lending.ai_extraction_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class AiExtractionServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiExtractionServiceApplication.class, args);
	}
}

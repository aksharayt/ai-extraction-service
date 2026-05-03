package com.lending.ai_extraction_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${anthropic.base-url}")
    private String anthropicBaseUrl;

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${anthropic.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public WebClient anthropicWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(anthropicBaseUrl)
                .defaultHeader("x-api-key", anthropicApiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
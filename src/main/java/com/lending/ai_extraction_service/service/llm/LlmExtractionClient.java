package com.lending.ai_extraction_service.service.llm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Client for the Anthropic Claude API.
 * All text passed here has already been through PiiRedactionService.
 * This class is responsible for prompt construction and response parsing only.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmExtractionClient {

    private final WebClient anthropicWebClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.model:claude-3-haiku-20240307}")
    private String model;

    @Value("${anthropic.max-tokens:1024}")
    private int maxTokens;

    private static final String EXTRACTION_SYSTEM_PROMPT = """
            You are a financial document parser. Your only task is to extract structured data
            from pay stub text. You must respond ONLY with valid JSON, no explanation, no markdown,
            no preamble. If a field cannot be determined, use null.
            
            Return this exact JSON structure:
            {
              "employer_name": "string or null",
              "gross_monthly_income": number or null,
              "ytd_total": number or null,
              "pay_period_start": "YYYY-MM-DD or null",
              "pay_period_end": "YYYY-MM-DD or null",
              "pay_frequency": "WEEKLY|BIWEEKLY|SEMIMONTHLY|MONTHLY or null",
              "employee_name_token": "string or null",
              "confidence_score": number between 0.0 and 1.0,
              "extraction_notes": "string describing any ambiguities"
            }
            """;

    @Retryable(
            retryFor = {WebClientResponseException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public LlmExtractionResult extract(String redactedPayStubText) {
        log.info("Sending redacted pay stub text to LLM. Model: {}", model);

        String userMessage = "Extract the pay stub data from the following text:\n\n" + redactedPayStubText;

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", EXTRACTION_SYSTEM_PROMPT,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                )
        );

        try {
            String response = anthropicWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response);

        } catch (WebClientResponseException e) {
            log.error("LLM API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private LlmExtractionResult parseResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String content = root.path("content").get(0).path("text").asText();

            // Guard against markdown-wrapped JSON
            content = content.strip();
            if (content.startsWith("```")) {
                content = content.replaceAll("```json|```", "").strip();
            }

            JsonNode extracted = objectMapper.readTree(content);
            return new LlmExtractionResult(
                    extracted.path("employer_name").asText(null),
                    extracted.path("gross_monthly_income").isNull() ? null
                            : extracted.path("gross_monthly_income").decimalValue(),
                    extracted.path("ytd_total").isNull() ? null
                            : extracted.path("ytd_total").decimalValue(),
                    extracted.path("pay_period_start").asText(null),
                    extracted.path("pay_period_end").asText(null),
                    extracted.path("pay_frequency").asText(null),
                    extracted.path("employee_name_token").asText(null),
                    extracted.path("confidence_score").asDouble(0.0),
                    extracted.path("extraction_notes").asText(null)
            );
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return LlmExtractionResult.failed();
        }
    }
}
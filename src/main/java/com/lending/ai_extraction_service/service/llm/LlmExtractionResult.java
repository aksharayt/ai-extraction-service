package com.lending.ai_extraction_service.service.llm;

import java.math.BigDecimal;

public record LlmExtractionResult(
        String employerName,
        BigDecimal grossMonthlyIncome,
        BigDecimal ytdTotal,
        String payPeriodStart,
        String payPeriodEnd,
        String payFrequency,
        String employeeNameToken,
        double confidenceScore,
        String extractionNotes
) {
    public boolean isFailed() {
        return employerName == null && grossMonthlyIncome == null && ytdTotal == null;
    }

    public static LlmExtractionResult failed() {
        return new LlmExtractionResult(null, null, null, null, null, null, null, 0.0, "EXTRACTION_FAILED");
    }
}
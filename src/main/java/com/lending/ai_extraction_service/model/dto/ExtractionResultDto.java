package com.lending.ai_extraction_service.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExtractionResultDto {

    private String extractionId;
    private String applicationId;
    private String documentId;

    // Extracted fields
    private String employerName;
    private BigDecimal grossMonthlyIncome;
    private BigDecimal ytdTotal;
    private String payPeriodStart;
    private String payPeriodEnd;
    private String payFrequency;
    private String employeeNameExtracted;

    // Discrepancy flags
    private boolean hasIncomeDiscrepancy;
    private boolean hasEmployerDiscrepancy;
    private BigDecimal incomeDiscrepancyAmount;
    private String discrepancyNotes;
    private String discrepancyRiskLevel; // LOW, MEDIUM, HIGH

    // Metadata
    private String reviewStatus;
    private Double confidenceScore;
    private String extractionModel;
    private LocalDateTime createdAt;

    // Self-reported (for side-by-side comparison in UI)
    private BigDecimal selfReportedMonthlyIncome;
    private String selfReportedEmployerName;
}
package com.lending.ai_extraction_service.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReviewDecisionDto {

    @NotBlank
    private String extractionId;

    @NotNull
    private String decision; // APPROVED, REJECTED, EDITED_AND_APPROVED, NEEDS_CLARIFICATION

    // Loan officer can override any field
    private String overrideEmployerName;
    private BigDecimal overrideGrossMonthlyIncome;
    private BigDecimal overrideYtdTotal;
    private String overridePayPeriodStart;
    private String overridePayPeriodEnd;

    private String loanOfficerNotes;
}
package com.lending.ai_extraction_service.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ExtractionRequestDto {

    @NotBlank
    private String applicationId;

    @NotBlank
    private String documentId;

    // Self-reported values from the application — used for discrepancy check
    private BigDecimal selfReportedMonthlyIncome;
    private String selfReportedEmployerName;
}

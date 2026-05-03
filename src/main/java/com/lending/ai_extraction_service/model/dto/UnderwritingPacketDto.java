package com.lending.ai_extraction_service.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Matches the existing Underwriting Service API contract exactly.
 * This service cannot be modified — we produce output that fits it.
 */
@Data
public class UnderwritingPacketDto {

    private String applicationId;
    private String verifiedEmployerName;
    private BigDecimal verifiedMonthlyIncome;
    private BigDecimal verifiedYtdTotal;
    private String incomeVerificationMethod; // "AI_ASSISTED_HUMAN_APPROVED"
    private boolean incomeVerified;
    private boolean employerVerified;
    private String verifiedByOfficerId;
    private LocalDateTime verifiedAt;
    private String documentReferenceId;
    private String auditTrailId;
}
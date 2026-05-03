package com.lending.ai_extraction_service.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_results")
@Data
public class ExtractionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "employer_name")
    private String employerName;

    @Column(name = "gross_monthly_income", precision = 12, scale = 2)
    private BigDecimal grossMonthlyIncome;

    @Column(name = "ytd_total", precision = 12, scale = 2)
    private BigDecimal ytdTotal;

    @Column(name = "pay_period_start")
    private String payPeriodStart;

    @Column(name = "pay_period_end")
    private String payPeriodEnd;

    @Column(name = "pay_frequency")
    private String payFrequency;

    @Column(name = "employee_name_extracted")
    private String employeeNameExtracted;

    @Column(name = "has_income_discrepancy")
    private boolean hasIncomeDiscrepancy;

    @Column(name = "has_employer_discrepancy")
    private boolean hasEmployerDiscrepancy;

    @Column(name = "income_discrepancy_amount", precision = 12, scale = 2)
    private BigDecimal incomeDiscrepancyAmount;

    @Column(name = "discrepancy_notes", length = 1000)
    private String discrepancyNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING_REVIEW;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "loan_officer_notes", length = 2000)
    private String loanOfficerNotes;

    @Column(name = "llm_confidence_score")
    private Double confidenceScore;

    @Column(name = "extraction_model")
    private String extractionModel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReviewStatus {
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        NEEDS_CLARIFICATION,
        EDITED_AND_APPROVED
    }
}
package com.lending.ai_extraction_service.service.extraction;

import com.lending.ai_extraction_service.model.dto.*;
import com.lending.ai_extraction_service.model.entity.ExtractionResult;
import com.lending.ai_extraction_service.model.entity.ExtractionResult.ReviewStatus;
import com.lending.ai_extraction_service.repository.ExtractionResultRepository;
import com.lending.ai_extraction_service.service.llm.LlmExtractionClient;
import com.lending.ai_extraction_service.service.llm.LlmExtractionResult;
import com.lending.ai_extraction_service.service.pii.PiiRedactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PayStubExtractionService {

    private final PiiRedactionService piiRedactionService;
    private final LlmExtractionClient llmClient;
    private final ExtractionResultRepository repository;
    private final DocumentTextExtractor documentTextExtractor;

    // Threshold: flag discrepancy if extracted income differs from self-reported by more than 5%
    private static final BigDecimal DISCREPANCY_THRESHOLD_PERCENT = new BigDecimal("0.05");

    @Transactional
    public ExtractionResultDto extractFromDocument(ExtractionRequestDto request, String documentS3Key) {

        log.info("Starting extraction pipeline for applicationId={}, documentId={}",
                request.getApplicationId(), request.getDocumentId());

        // Step 1: Pull raw text from document (stubbed — would call Document Service)
        String rawText = documentTextExtractor.extractText(documentS3Key);

        // Step 2: Redact PII before anything external sees the text
        PiiRedactionService.RedactionResult redactionResult = piiRedactionService.redact(rawText);
        log.info("Redacted {} PII tokens before LLM call", redactionResult.redactedTokenCount());

        // Step 3: Send redacted text to LLM
        LlmExtractionResult llmResult = llmClient.extract(redactionResult.redactedText());

        if (llmResult.isFailed()) {
            log.error("LLM extraction failed for documentId={}", request.getDocumentId());
            throw new ExtractionFailedException("LLM extraction failed — document may be unreadable or corrupted");
        }

        // Step 4: Discrepancy analysis
        DiscrepancyAnalysis discrepancy = analyzeDiscrepancies(llmResult, request);

        // Step 5: Persist result with PENDING_REVIEW status
        ExtractionResult entity = buildEntity(request, llmResult, discrepancy);
        entity = repository.save(entity);

        log.info("Extraction complete. extractionId={}, hasDiscrepancy={}, confidence={}",
                entity.getId(), discrepancy.hasAnyDiscrepancy(), llmResult.confidenceScore());

        return toDto(entity, request.getSelfReportedMonthlyIncome(), request.getSelfReportedEmployerName());
    }

    @Transactional
    public ExtractionResultDto applyReviewDecision(ReviewDecisionDto decision, String officerId) {

        ExtractionResult entity = repository.findById(decision.getExtractionId())
                .orElseThrow(() -> new ExtractionNotFoundException(decision.getExtractionId()));

        ReviewStatus newStatus = ReviewStatus.valueOf(decision.getDecision());
        entity.setReviewStatus(newStatus);
        entity.setReviewedBy(officerId);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setLoanOfficerNotes(decision.getLoanOfficerNotes());

        // Apply any overrides the loan officer made
        if (decision.getOverrideEmployerName() != null) {
            entity.setEmployerName(decision.getOverrideEmployerName());
        }
        if (decision.getOverrideGrossMonthlyIncome() != null) {
            entity.setGrossMonthlyIncome(decision.getOverrideGrossMonthlyIncome());
        }
        if (decision.getOverrideYtdTotal() != null) {
            entity.setYtdTotal(decision.getOverrideYtdTotal());
        }
        if (decision.getOverridePayPeriodStart() != null) {
            entity.setPayPeriodStart(decision.getOverridePayPeriodStart());
        }
        if (decision.getOverridePayPeriodEnd() != null) {
            entity.setPayPeriodEnd(decision.getOverridePayPeriodEnd());
        }

        entity = repository.save(entity);
        log.info("Review decision applied: extractionId={}, decision={}, officer={}",
                entity.getId(), newStatus, officerId);

        return toDto(entity, null, null);
    }

    public UnderwritingPacketDto buildUnderwritingPacket(String extractionId) {
        ExtractionResult entity = repository.findById(extractionId)
                .orElseThrow(() -> new ExtractionNotFoundException(extractionId));

        if (entity.getReviewStatus() != ReviewStatus.APPROVED
                && entity.getReviewStatus() != ReviewStatus.EDITED_AND_APPROVED) {
            throw new IllegalStateException("Cannot build underwriting packet — extraction not yet approved by officer");
        }

        UnderwritingPacketDto packet = new UnderwritingPacketDto();
        packet.setApplicationId(entity.getApplicationId());
        packet.setVerifiedEmployerName(entity.getEmployerName());
        packet.setVerifiedMonthlyIncome(entity.getGrossMonthlyIncome());
        packet.setVerifiedYtdTotal(entity.getYtdTotal());
        packet.setIncomeVerificationMethod("AI_ASSISTED_HUMAN_APPROVED");
        packet.setIncomeVerified(true);
        packet.setEmployerVerified(true);
        packet.setVerifiedByOfficerId(entity.getReviewedBy());
        packet.setVerifiedAt(entity.getReviewedAt());
        packet.setDocumentReferenceId(entity.getDocumentId());
        packet.setAuditTrailId(entity.getId());

        return packet;
    }

    private DiscrepancyAnalysis analyzeDiscrepancies(LlmExtractionResult llm, ExtractionRequestDto request) {
        boolean incomeDiscrepancy = false;
        boolean employerDiscrepancy = false;
        BigDecimal incomeDiff = BigDecimal.ZERO;
        StringBuilder notes = new StringBuilder();

        if (request.getSelfReportedMonthlyIncome() != null && llm.grossMonthlyIncome() != null) {
            BigDecimal reported = request.getSelfReportedMonthlyIncome();
            BigDecimal extracted = llm.grossMonthlyIncome();
            incomeDiff = extracted.subtract(reported).abs();
            BigDecimal threshold = reported.multiply(DISCREPANCY_THRESHOLD_PERCENT);

            if (incomeDiff.compareTo(threshold) > 0) {
                incomeDiscrepancy = true;
                notes.append("Income discrepancy: self-reported $")
                        .append(reported).append(", extracted $").append(extracted)
                        .append(", difference $").append(incomeDiff).append(". ");
            }
        }

        if (request.getSelfReportedEmployerName() != null && llm.employerName() != null) {
            // Simple normalized comparison
            String reported = request.getSelfReportedEmployerName().toLowerCase().strip();
            String extracted = llm.employerName().toLowerCase().strip();
            if (!reported.contains(extracted) && !extracted.contains(reported)) {
                employerDiscrepancy = true;
                notes.append("Employer name mismatch: self-reported '")
                        .append(request.getSelfReportedEmployerName())
                        .append("', extracted '").append(llm.employerName()).append("'. ");
            }
        }

        return new DiscrepancyAnalysis(incomeDiscrepancy, employerDiscrepancy, incomeDiff, notes.toString());
    }

    private ExtractionResult buildEntity(ExtractionRequestDto request,
                                         LlmExtractionResult llm,
                                         DiscrepancyAnalysis discrepancy) {
        ExtractionResult entity = new ExtractionResult();
        entity.setApplicationId(request.getApplicationId());
        entity.setDocumentId(request.getDocumentId());
        entity.setEmployerName(llm.employerName());
        entity.setGrossMonthlyIncome(llm.grossMonthlyIncome());
        entity.setYtdTotal(llm.ytdTotal());
        entity.setPayPeriodStart(llm.payPeriodStart());
        entity.setPayPeriodEnd(llm.payPeriodEnd());
        entity.setPayFrequency(llm.payFrequency());
        entity.setEmployeeNameExtracted(llm.employeeNameToken());
        entity.setHasIncomeDiscrepancy(discrepancy.hasIncomeDiscrepancy());
        entity.setHasEmployerDiscrepancy(discrepancy.hasEmployerDiscrepancy());
        entity.setIncomeDiscrepancyAmount(discrepancy.incomeDiscrepancyAmount());
        entity.setDiscrepancyNotes(discrepancy.notes());
        entity.setConfidenceScore(llm.confidenceScore());
        entity.setExtractionModel("claude-3-haiku-20240307");
        entity.setReviewStatus(ReviewStatus.PENDING_REVIEW);
        return entity;
    }

    private ExtractionResultDto toDto(ExtractionResult e,
                                      BigDecimal selfReportedIncome,
                                      String selfReportedEmployer) {
        ExtractionResultDto dto = new ExtractionResultDto();
        dto.setExtractionId(e.getId());
        dto.setApplicationId(e.getApplicationId());
        dto.setDocumentId(e.getDocumentId());
        dto.setEmployerName(e.getEmployerName());
        dto.setGrossMonthlyIncome(e.getGrossMonthlyIncome());
        dto.setYtdTotal(e.getYtdTotal());
        dto.setPayPeriodStart(e.getPayPeriodStart());
        dto.setPayPeriodEnd(e.getPayPeriodEnd());
        dto.setPayFrequency(e.getPayFrequency());
        dto.setEmployeeNameExtracted(e.getEmployeeNameExtracted());
        dto.setHasIncomeDiscrepancy(e.isHasIncomeDiscrepancy());
        dto.setHasEmployerDiscrepancy(e.isHasEmployerDiscrepancy());
        dto.setIncomeDiscrepancyAmount(e.getIncomeDiscrepancyAmount());
        dto.setDiscrepancyNotes(e.getDiscrepancyNotes());
        dto.setReviewStatus(e.getReviewStatus().name());
        dto.setConfidenceScore(e.getConfidenceScore());
        dto.setExtractionModel(e.getExtractionModel());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setSelfReportedMonthlyIncome(selfReportedIncome);
        dto.setSelfReportedEmployerName(selfReportedEmployer);

        // Risk level logic
        if (e.isHasIncomeDiscrepancy() && e.isHasEmployerDiscrepancy()) {
            dto.setDiscrepancyRiskLevel("HIGH");
        } else if (e.isHasIncomeDiscrepancy() || e.isHasEmployerDiscrepancy()) {
            dto.setDiscrepancyRiskLevel("MEDIUM");
        } else {
            dto.setDiscrepancyRiskLevel("LOW");
        }

        return dto;
    }

    private record DiscrepancyAnalysis(
            boolean hasIncomeDiscrepancy,
            boolean hasEmployerDiscrepancy,
            BigDecimal incomeDiscrepancyAmount,
            String notes
    ) {
        boolean hasAnyDiscrepancy() {
            return hasIncomeDiscrepancy || hasEmployerDiscrepancy;
        }
    }
}
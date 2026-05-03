package com.lending.ai_extraction_service.controller;

import com.lending.ai_extraction_service.model.dto.*;
import com.lending.ai_extraction_service.service.extraction.PayStubExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/extractions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pay Stub Extraction", description = "GenAI-powered pay stub extraction and review workflow")
@SecurityRequirement(name = "bearerAuth")
public class ExtractionController {

    private final PayStubExtractionService extractionService;

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    @Operation(summary = "Trigger AI extraction for an uploaded pay stub",
            description = "Accepts document metadata, runs PII redaction, calls LLM API, returns structured extraction awaiting human review")
    public ResponseEntity<ExtractionResultDto> triggerExtraction(
            @Valid @RequestBody ExtractionRequestDto request) {

        log.info("Extraction triggered by officer for applicationId={}", request.getApplicationId());
        // In production: document s3Key would be fetched from Document Service
        String mockS3Key = "documents/" + request.getDocumentId() + ".pdf";
        ExtractionResultDto result = extractionService.extractFromDocument(request, mockS3Key);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/application/{applicationId}")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN', 'UNDERWRITER')")
    @Operation(summary = "Get all extractions for an application")
    public ResponseEntity<List<ExtractionResultDto>> getByApplication(
            @PathVariable String applicationId) {
        // Simplified for POC — repository would be called here
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{extractionId}/review")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    @Operation(summary = "Submit loan officer review decision",
            description = "Loan officer approves, edits, or rejects AI-extracted data. Only approved extractions flow to underwriting.")
    public ResponseEntity<ExtractionResultDto> submitReview(
            @PathVariable String extractionId,
            @Valid @RequestBody ReviewDecisionDto decision,
            @AuthenticationPrincipal Jwt jwt) {

        decision.setExtractionId(extractionId);
        String officerId = jwt.getSubject();
        ExtractionResultDto result = extractionService.applyReviewDecision(decision, officerId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{extractionId}/underwriting-packet")
    @PreAuthorize("hasAnyRole('UNDERWRITER', 'ADMIN')")
    @Operation(summary = "Generate underwriting packet from approved extraction",
            description = "Returns structured data compatible with the Underwriting Service API contract. Only available after loan officer approval.")
    public ResponseEntity<UnderwritingPacketDto> getUnderwritingPacket(
            @PathVariable String extractionId) {

        UnderwritingPacketDto packet = extractionService.buildUnderwritingPacket(extractionId);
        return ResponseEntity.ok(packet);
    }

    @GetMapping("/queue/pending")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER', 'ADMIN')")
    @Operation(summary = "Get all extractions pending review")
    public ResponseEntity<String> getPendingQueue() {
        // Simplified for POC
        return ResponseEntity.ok("{\"message\": \"Queue endpoint active\"}");
    }
}

package com.lending.ai_extraction_service.repository;

import com.lending.ai_extraction_service.model.entity.ExtractionResult;
import com.lending.ai_extraction_service.model.entity.ExtractionResult.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtractionResultRepository extends JpaRepository<ExtractionResult, String> {

    List<ExtractionResult> findByApplicationId(String applicationId);

    List<ExtractionResult> findByApplicationIdAndReviewStatus(String applicationId, ReviewStatus status);

    Optional<ExtractionResult> findByDocumentId(String documentId);

    @Query("SELECT e FROM ExtractionResult e WHERE e.reviewStatus = 'PENDING_REVIEW' ORDER BY e.createdAt ASC")
    List<ExtractionResult> findAllPendingReview();

    @Query("SELECT e FROM ExtractionResult e WHERE (e.hasIncomeDiscrepancy = true OR e.hasEmployerDiscrepancy = true) AND e.reviewStatus = 'PENDING_REVIEW'")
    List<ExtractionResult> findAllWithDiscrepanciesPendingReview();
}
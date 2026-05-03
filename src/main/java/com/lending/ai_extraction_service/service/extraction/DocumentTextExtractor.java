package com.lending.ai_extraction_service.service.extraction;

import org.springframework.stereotype.Component;

/**
 * In production: calls the Document Service to fetch the S3 object,
 * then uses Apache Tika or AWS Textract to extract text from the PDF.
 * For this POC: returns realistic mock pay stub text.
 */
@Component
public class DocumentTextExtractor {

    public String extractText(String s3Key) {
        // POC STUB — replace with real S3 + Textract call in production
        return """
                ACME CORPORATION
                123 Business Park Drive, Austin, TX 78701
                EIN: 12-3456789
                
                PAY STUB
                Pay Period: 11/01/2024 - 11/15/2024
                Pay Date: 11/20/2024
                
                Employee: EMPLOYEE_NAME_REDACTED
                SSN: SSN_REDACTED
                Department: Engineering
                
                EARNINGS
                Regular Pay:       $3,750.00
                Overtime:          $0.00
                Gross Pay:         $3,750.00
                
                YTD Gross:         $82,500.00
                
                DEDUCTIONS
                Federal Tax:       $562.50
                State Tax:         $187.50
                Social Security:   $232.50
                Medicare:          $54.38
                
                Net Pay:           $2,713.12
                
                Pay Frequency: Semi-Monthly
                """;
    }
}
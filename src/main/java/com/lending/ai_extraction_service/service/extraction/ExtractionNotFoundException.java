package com.lending.ai_extraction_service.service.extraction;

public class ExtractionNotFoundException extends RuntimeException {
    public ExtractionNotFoundException(String id) {
        super("Extraction not found: " + id);
    }
}

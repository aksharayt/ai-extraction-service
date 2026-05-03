package com.lending.ai_extraction_service.service.extraction;

public class ExtractionFailedException extends RuntimeException {
    public ExtractionFailedException(String message) {
        super(message);
    }
}
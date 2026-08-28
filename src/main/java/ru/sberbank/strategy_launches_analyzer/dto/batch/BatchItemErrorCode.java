package ru.sberbank.strategy_launches_analyzer.dto.batch;

public enum BatchItemErrorCode {
    MALFORMED_JSON,
    INVALID_ENCODING,
    VALIDATION_ERROR,
    DUPLICATE_REQUEST_ID,
    PROCESSING_ERROR
}

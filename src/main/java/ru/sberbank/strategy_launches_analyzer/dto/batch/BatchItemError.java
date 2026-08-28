package ru.sberbank.strategy_launches_analyzer.dto.batch;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchItemError(
        @JsonProperty(required = true)
        BatchItemErrorCode code,
        @JsonProperty(required = true)
        String message
) {
}

package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ErrorResponse(
        @JsonProperty(required = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        @JsonProperty(required = true)
        int status,
        @JsonProperty(required = true)
        String error,
        @JsonProperty(required = true)
        String message
) {
}

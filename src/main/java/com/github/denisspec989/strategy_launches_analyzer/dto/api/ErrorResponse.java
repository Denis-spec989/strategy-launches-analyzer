package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record ErrorResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp,
        int status,
        String error,
        String message
) {
}

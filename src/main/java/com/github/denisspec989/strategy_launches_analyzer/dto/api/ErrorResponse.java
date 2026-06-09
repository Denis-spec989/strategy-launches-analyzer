package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message
) {
}

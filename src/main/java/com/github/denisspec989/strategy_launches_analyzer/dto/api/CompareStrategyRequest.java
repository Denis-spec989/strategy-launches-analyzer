package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CompareStrategyRequest(
        @NotNull(message = "strategy is required.")
        StrategyName strategy,
        @NotNull(message = "mainLaunch is required.")
        JsonNode mainLaunch,
        @NotNull(message = "shadowLaunch is required.")
        JsonNode shadowLaunch,
        @Valid
        @NotNull(message = "metadata is required.")
        LaunchMetadata metadata
) {
}

package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.databind.JsonNode;

public record CompareStrategyRequest(
        JsonNode mainLaunch,
        JsonNode shadowLaunch,
        LaunchMetadata metadata
) {
}

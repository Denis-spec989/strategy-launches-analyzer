package ru.sberbank.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.JsonNode;

record BenchmarkCase(
        String id,
        java.util.List<String> tags,
        JsonNode mainLaunch,
        JsonNode shadowLaunch,
        SemanticExpectations semantic
) {
}

package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.util.List;

record ModelBenchmarkSummary(
        String model,
        int expectedSamples,
        int recordedSamples,
        double apiSuccessRate,
        double hardPassRate,
        double semanticMean,
        double semanticMinimum,
        double guardrailCorrectionRate,
        long p95LatencyMs,
        double averageInputTokens,
        double averageOutputTokens,
        double averageCacheReadInputTokens,
        double averageCacheWriteInputTokens,
        boolean eligible,
        List<String> exclusionReasons
) {
    ModelBenchmarkSummary {
        exclusionReasons = exclusionReasons == null ? List.of() : List.copyOf(exclusionReasons);
    }
}

package ru.sberbank.strategy_launches_analyzer.benchmark;

import java.util.List;

record BenchmarkSummary(
        String runId,
        boolean incomplete,
        String winner,
        List<String> globalIssues,
        List<ModelBenchmarkSummary> models,
        List<String> limitations
) {
}

package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

record BenchmarkConfiguration(
        List<String> models,
        String judgeModel,
        int repetitions,
        int concurrency,
        long shuffleSeed,
        Path resumeFrom
) {
    private static final String DEFAULT_MODELS = "gpt-5.4,gpt-5.5";

    static BenchmarkConfiguration fromSystemProperties() {
        List<String> models = Arrays.stream(System.getProperty("benchmark.models", DEFAULT_MODELS).split(","))
                .map(String::trim)
                .filter(model -> !model.isEmpty())
                .distinct()
                .toList();
        if (models.size() < 2) {
            throw new IllegalArgumentException("benchmark.models must contain at least two distinct models.");
        }
        int repetitions = Integer.parseInt(System.getProperty("benchmark.repetitions", "3"));
        if (repetitions < 1) {
            throw new IllegalArgumentException("benchmark.repetitions must be positive.");
        }
        int concurrency = Integer.parseInt(System.getProperty("benchmark.concurrency", "1"));
        if (concurrency != 1) {
            throw new IllegalArgumentException(
                    "benchmark.concurrency must be 1 in v1; candidate calls are intentionally sequential."
            );
        }
        String resume = System.getProperty("benchmark.resume-from");
        return new BenchmarkConfiguration(
                models,
                System.getProperty("benchmark.judge-model", "gpt-5.6-sol"),
                repetitions,
                concurrency,
                Long.parseLong(System.getProperty("benchmark.shuffle-seed", "42")),
                resume == null || resume.isBlank() ? null : Path.of(resume).toAbsolutePath().normalize()
        );
    }
}

package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

record AutomatedBenchmarkConfiguration(
        List<String> candidateModels,
        Path judgeSelectionReport,
        Path outputBaseDirectory,
        Path resumeFrom,
        int pilotRepetitions,
        int fullRepetitions,
        int finalistCount,
        long shuffleSeed,
        double minimumSemanticGap
) {
    AutomatedBenchmarkConfiguration {
        candidateModels = candidateModels == null ? List.of() : candidateModels.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (candidateModels.size() < 2) {
            throw new IllegalArgumentException("model-selection.candidate-models must contain at least two models.");
        }
        if (pilotRepetitions < 1 || fullRepetitions < 2) {
            throw new IllegalArgumentException("Pilot repetitions must be positive and full repetitions at least two.");
        }
        if (finalistCount < 2) {
            throw new IllegalArgumentException("model-selection.finalists must be at least two.");
        }
        if (minimumSemanticGap < 0) {
            throw new IllegalArgumentException("model-selection.minimum-semantic-gap must not be negative.");
        }
        judgeSelectionReport = judgeSelectionReport.toAbsolutePath().normalize();
        outputBaseDirectory = outputBaseDirectory.toAbsolutePath().normalize();
        resumeFrom = resumeFrom == null ? null : resumeFrom.toAbsolutePath().normalize();
    }

    static AutomatedBenchmarkConfiguration fromSystemProperties() {
        String resume = System.getProperty("model-selection.resume-from");
        return new AutomatedBenchmarkConfiguration(
                csv(requiredProperty("model-selection.candidate-models")),
                Path.of(System.getProperty(
                        "model-selection.judge-selection-report",
                        "target/model-selection/judges/selection.json"
                )),
                Path.of(System.getProperty(
                        "model-selection.output-base",
                        "target/model-selection/runs"
                )),
                resume == null || resume.isBlank() ? null : Path.of(resume),
                Integer.parseInt(System.getProperty("model-selection.pilot-repetitions", "1")),
                Integer.parseInt(System.getProperty("model-selection.full-repetitions", "3")),
                Integer.parseInt(System.getProperty("model-selection.finalists", "3")),
                Long.parseLong(System.getProperty("model-selection.shuffle-seed", "42")),
                Double.parseDouble(System.getProperty("model-selection.minimum-semantic-gap", "0.02"))
        );
    }

    private static List<String> csv(String value) {
        return Arrays.stream(value.split(",")).toList();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }
}

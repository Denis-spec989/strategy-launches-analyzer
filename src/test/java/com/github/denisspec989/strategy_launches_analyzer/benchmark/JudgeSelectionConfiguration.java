package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

record JudgeSelectionConfiguration(
        List<String> judgeModels,
        Path dataset,
        Path outputDirectory
) {
    JudgeSelectionConfiguration {
        judgeModels = judgeModels == null ? List.of() : judgeModels.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (judgeModels.size() < 2) {
            throw new IllegalArgumentException("model-selection.judge-models must contain at least two models.");
        }
        dataset = dataset.toAbsolutePath().normalize();
        outputDirectory = outputDirectory.toAbsolutePath().normalize();
    }

    static JudgeSelectionConfiguration fromSystemProperties() {
        return new JudgeSelectionConfiguration(
                csv(requiredProperty("model-selection.judge-models")),
                Path.of(System.getProperty(
                        "model-selection.calibration-dataset",
                        "src/test/resources/evals/judge-calibration/v2/calibration-cases.jsonl"
                )),
                Path.of(System.getProperty(
                        "model-selection.judge-output",
                        "target/model-selection/judges"
                ))
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

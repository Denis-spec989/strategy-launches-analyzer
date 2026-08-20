package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

final class JudgeSelectionRunner {
    static final String REPORT_SCHEMA_VERSION = "gigachat-judge-selection/v1";

    private final ObjectMapper objectMapper;
    private final Supplier<List<String>> availableChatModels;
    private final JudgeCalibrationRunner calibrationRunner;

    JudgeSelectionRunner(
            ObjectMapper objectMapper,
            Supplier<List<String>> availableChatModels,
            JudgeCalibrationRunner calibrationRunner
    ) {
        this.objectMapper = objectMapper;
        this.availableChatModels = availableChatModels;
        this.calibrationRunner = calibrationRunner;
    }

    JudgeSelectionReport run(JudgeSelectionConfiguration configuration) {
        List<String> available = availableChatModels.get().stream().distinct().sorted().toList();
        List<JudgeCandidateCalibration> candidates = new ArrayList<>();
        for (String model : configuration.judgeModels()) {
            if (!available.contains(model)) {
                candidates.add(JudgeCandidateCalibration.unavailable(model));
                continue;
            }
            Path modelDirectory = configuration.outputDirectory().resolve(safeFileName(model));
            try {
                JudgeCalibrationReport report = calibrationRunner.run(
                        configuration.dataset(),
                        modelDirectory.resolve("results.jsonl"),
                        model
                );
                candidates.add(JudgeCandidateCalibration.completed(
                        report,
                        modelDirectory.resolve("report.json").toAbsolutePath().normalize().toString()
                ));
            } catch (RuntimeException ex) {
                candidates.add(JudgeCandidateCalibration.failed(model, error(ex)));
            }
        }

        List<JudgeCandidateCalibration> accepted = rankAccepted(candidates);
        String selected = accepted.isEmpty() ? null : accepted.get(0).model();
        String alternate = accepted.size() < 2 ? null : accepted.get(1).model();
        String decision = selected == null
                ? "No judge candidate passed calibration."
                : "Selected by zero unsafe false negatives, highest pass/fail agreement, then lowest mean and maximum axis MAE.";
        JudgeSelectionReport report = new JudgeSelectionReport(
                REPORT_SCHEMA_VERSION,
                Instant.now(),
                configuration.dataset().toString(),
                BenchmarkHashes.fileHash(configuration.dataset()),
                available,
                candidates,
                selected,
                alternate,
                decision
        );
        writeReport(configuration.outputDirectory().resolve("selection.json"), report);
        return report;
    }

    static List<JudgeCandidateCalibration> rankAccepted(List<JudgeCandidateCalibration> candidates) {
        return candidates.stream()
                .filter(JudgeCandidateCalibration::accepted)
                .sorted(Comparator
                        .comparingDouble((JudgeCandidateCalibration item) -> item.passFailAgreement()).reversed()
                        .thenComparingDouble(JudgeCandidateCalibration::meanAxisMae)
                        .thenComparingDouble(JudgeCandidateCalibration::maximumAxisMae)
                        .thenComparing(JudgeCandidateCalibration::model))
                .toList();
    }

    private void writeReport(Path path, JudgeSelectionReport report) {
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write judge selection report " + path, ex);
        }
    }

    static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String error(Throwable ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}

package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JudgeCalibrationStore {
    private final ObjectMapper objectMapper;
    private final Path resultsPath;

    JudgeCalibrationStore(ObjectMapper objectMapper, Path resultsPath) {
        this.objectMapper = objectMapper;
        this.resultsPath = resultsPath.toAbsolutePath().normalize();
    }

    Map<String, JudgeCalibrationCase> readLatest() {
        Map<String, JudgeCalibrationCase> latest = new LinkedHashMap<>();
        if (!Files.isRegularFile(resultsPath)) {
            return latest;
        }
        try {
            for (String line : Files.readAllLines(resultsPath, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    JudgeCalibrationCase item = objectMapper.readValue(line, JudgeCalibrationCase.class);
                    latest.put(item.id(), item);
                }
            }
            return latest;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to resume judge calibration from " + resultsPath, ex);
        }
    }

    void append(JudgeCalibrationCase item) {
        try {
            Files.createDirectories(resultsPath.getParent());
            Files.writeString(
                    resultsPath,
                    objectMapper.writeValueAsString(item) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to append judge calibration result.", ex);
        }
    }

    void writeReport(JudgeCalibrationReport report) {
        try {
            Files.createDirectories(resultsPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(resultsPath.getParent().resolve("report.json").toFile(), report);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write judge calibration report.", ex);
        }
    }

    static List<JudgeCalibrationCase> readDataset(ObjectMapper objectMapper, Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Calibration dataset does not exist: " + path.toAbsolutePath());
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> readCase(objectMapper, line))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read calibration dataset.", ex);
        }
    }

    private static JudgeCalibrationCase readCase(ObjectMapper objectMapper, String line) {
        try {
            return objectMapper.readValue(line, JudgeCalibrationCase.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid calibration JSONL record.", ex);
        }
    }
}

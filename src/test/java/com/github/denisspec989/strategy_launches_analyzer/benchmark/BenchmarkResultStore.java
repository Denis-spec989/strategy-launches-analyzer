package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BenchmarkResultStore {
    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final Path runDirectory;
    private final Path resultsPath;

    private BenchmarkResultStore(ObjectMapper objectMapper, Path runDirectory) {
        this.objectMapper = objectMapper;
        this.runDirectory = runDirectory;
        this.resultsPath = runDirectory.resolve("results.jsonl");
    }

    static BenchmarkResultStore open(
            ObjectMapper objectMapper,
            BenchmarkConfiguration configuration,
            BenchmarkManifest requestedManifest
    ) {
        try {
            if (configuration.resumeFrom() != null) {
                Path directory = configuration.resumeFrom();
                BenchmarkResultStore store = new BenchmarkResultStore(objectMapper, directory);
                BenchmarkManifest existing = objectMapper.readValue(
                        directory.resolve("manifest.json").toFile(), BenchmarkManifest.class
                );
                existing.requireCompatible(requestedManifest);
                Files.createDirectories(directory.resolve("failures"));
                return store;
            }
            Path directory = Path.of("target", "benchmark", requestedManifest.runId())
                    .toAbsolutePath().normalize();
            Files.createDirectories(directory.resolve("failures"));
            BenchmarkResultStore store = new BenchmarkResultStore(objectMapper, directory);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(directory.resolve("manifest.json").toFile(), requestedManifest);
            return store;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize benchmark result store.", ex);
        }
    }

    static String newRunId() {
        return RUN_ID_FORMAT.format(Instant.now());
    }

    Map<String, BenchmarkSampleResult> readLatestResults() {
        LinkedHashMap<String, BenchmarkSampleResult> results = new LinkedHashMap<>();
        if (!Files.isRegularFile(resultsPath)) {
            return results;
        }
        try {
            for (String line : Files.readAllLines(resultsPath, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    BenchmarkSampleResult result = objectMapper.readValue(line, BenchmarkSampleResult.class);
                    results.put(result.key(), result);
                }
            }
            return results;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read benchmark results " + resultsPath, ex);
        }
    }

    void append(BenchmarkSampleResult result) {
        try {
            Files.createDirectories(runDirectory);
            Files.writeString(
                    resultsPath,
                    objectMapper.writeValueAsString(result) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            if (result.status() != BenchmarkSampleStatus.SUCCESS) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                        runDirectory.resolve("failures")
                                .resolve(safeFileName(result.caseId() + "-" + result.model()
                                        + "-" + result.repetition()) + ".json")
                                .toFile(),
                        result
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to append benchmark result.", ex);
        }
    }

    Path runDirectory() {
        return runDirectory;
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

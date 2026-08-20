package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SavedResponseRejudgeRunner {
    private static final String SCHEMA_VERSION = "benchmark-saved-response-rejudge/v1";

    private final ObjectMapper objectMapper;
    private final SemanticJudge semanticJudge;
    private final BenchmarkInputFactory inputFactory;
    private final BenchmarkAggregator aggregator = new BenchmarkAggregator();

    SavedResponseRejudgeRunner(
            ObjectMapper objectMapper,
            SemanticJudge semanticJudge,
            BenchmarkInputFactory inputFactory
    ) {
        this.objectMapper = objectMapper;
        this.semanticJudge = semanticJudge;
        this.inputFactory = inputFactory;
    }

    BenchmarkSummary run(Path sourceRun, String judgeModel) {
        return run(sourceRun, judgeModel, null);
    }

    BenchmarkSummary run(Path sourceRun, String judgeModel, Path requestedOutput) {
        Path source = sourceRun.toAbsolutePath().normalize();
        Path sourceManifest = source.resolve("manifest.json");
        Path sourceResults = source.resolve("results.jsonl");
        requireFile(sourceManifest);
        requireFile(sourceResults);

        JsonNode sourceManifestJson = readTree(sourceManifest);
        List<String> manifestModels = new java.util.ArrayList<>();
        sourceManifestJson.path("models").forEach(item -> manifestModels.add(item.asText()));
        List<String> models = manifestModels.isEmpty()
                ? readSourceResults(sourceResults).stream().map(BenchmarkSampleResult::model).distinct().toList()
                : List.copyOf(manifestModels);
        int repetitions = sourceManifestJson.path("repetitions").asInt(0);
        if (repetitions < 1) {
            throw new IllegalStateException("Source benchmark manifest has no valid repetitions count.");
        }

        List<BenchmarkCase> cases = new BenchmarkCaseLoader(objectMapper).load(BenchmarkCaseLoader.DEFAULT_DATASET);
        Map<String, BenchmarkCase> casesById = new LinkedHashMap<>();
        Map<String, AgentAnalysisInput> inputsById = new LinkedHashMap<>();
        cases.forEach(item -> {
            casesById.put(item.id(), item);
            inputsById.put(item.id(), inputFactory.create(item));
        });

        List<BenchmarkSampleResult> sourceRows = List.copyOf(readLatest(sourceResults).values());
        int expected = cases.size() * models.size() * repetitions;
        if (sourceRows.size() != expected) {
            throw new IllegalStateException("Saved-response rejudge requires a complete source run: found "
                    + sourceRows.size() + " of " + expected + " samples.");
        }
        String rubricHash = BenchmarkHashes.judgePromptHash();
        Path output = requestedOutput == null
                ? Path.of("target", "benchmark-rejudge",
                        source.getFileName() + "-" + safeFileName(judgeModel)
                                + "-" + rubricHash.substring(0, 12)).toAbsolutePath().normalize()
                : requestedOutput.toAbsolutePath().normalize();
        BenchmarkRejudgeManifest requested = new BenchmarkRejudgeManifest(
                SCHEMA_VERSION,
                Instant.now(),
                source.getFileName().toString(),
                BenchmarkHashes.fileHash(sourceManifest),
                BenchmarkHashes.fileHash(sourceResults),
                BenchmarkHashes.datasetHash(BenchmarkCaseLoader.DEFAULT_DATASET),
                judgeModel,
                GigaChatSemanticJudge.RUBRIC_VERSION,
                rubricHash
        );
        initialize(output, requested);
        Path outputResults = output.resolve("results.jsonl");
        Map<String, BenchmarkSampleResult> latest = readLatest(outputResults);

        for (BenchmarkSampleResult sourceRow : sourceRows) {
            BenchmarkSampleResult previous = latest.get(sourceRow.key());
            if (sourceRow.finalAnalysis() == null || sourceRow.callResult() == null) {
                if (previous == null) {
                    append(outputResults, sourceRow);
                    latest.put(sourceRow.key(), sourceRow);
                }
                continue;
            }
            if (isComplete(previous)) {
                continue;
            }
            BenchmarkCase benchmarkCase = casesById.get(sourceRow.caseId());
            AgentAnalysisInput input = inputsById.get(sourceRow.caseId());
            if (benchmarkCase == null || input == null) {
                throw new IllegalStateException("Source run contains unknown case " + sourceRow.caseId());
            }
            SemanticGrade grade = semanticJudge.grade(
                    input, sourceRow.finalAnalysis(), benchmarkCase.semantic()
            );
            SafetyAdjudication adjudication = grade.safetyPass()
                    ? null
                    : semanticJudge.adjudicate(input, sourceRow.finalAnalysis(), benchmarkCase.semantic(), grade);
            BenchmarkSampleResult rejudged = new BenchmarkSampleResult(
                    sourceRow.caseId(), sourceRow.tags(), sourceRow.model(), sourceRow.repetition(),
                    BenchmarkSampleStatus.SUCCESS, sourceRow.callResult(), sourceRow.rawGrade(),
                    sourceRow.finalAnalysis(), sourceRow.corrections(), sourceRow.finalGrade(),
                    grade, adjudication, null
            );
            append(outputResults, rejudged);
            latest.put(rejudged.key(), rejudged);
        }

        BenchmarkSummary summary = aggregator.summarize(
                output.getFileName().toString(), models, List.copyOf(latest.values()), cases.size() * repetitions
        );
        new BenchmarkReportWriter(objectMapper).write(output, summary);
        System.out.println("Saved-response rejudge report: " + output.resolve("summary.md"));
        System.out.println("Candidate model API calls: 0");
        return summary;
    }

    private void initialize(Path output, BenchmarkRejudgeManifest requested) {
        try {
            Files.createDirectories(output);
            Path manifest = output.resolve("manifest.json");
            if (Files.isRegularFile(manifest)) {
                objectMapper.readValue(manifest.toFile(), BenchmarkRejudgeManifest.class)
                        .requireCompatible(requested);
            } else {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), requested);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize saved-response rejudge.", ex);
        }
    }

    private Map<String, BenchmarkSampleResult> readLatest(Path path) {
        Map<String, BenchmarkSampleResult> latest = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) {
            return latest;
        }
        readSourceResults(path).forEach(item -> BenchmarkResultStore.putLatest(latest, item));
        return latest;
    }

    private List<BenchmarkSampleResult> readSourceResults(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> readResult(path, line))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read benchmark results " + path, ex);
        }
    }

    private BenchmarkSampleResult readResult(Path path, String line) {
        try {
            return objectMapper.readValue(line, BenchmarkSampleResult.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid benchmark result in " + path, ex);
        }
    }

    private JsonNode readTree(Path path) {
        try {
            return objectMapper.readTree(path.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private void append(Path path, BenchmarkSampleResult result) {
        try {
            Files.writeString(path, objectMapper.writeValueAsString(result) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to append saved-response rejudge result.", ex);
        }
    }

    private static boolean isComplete(BenchmarkSampleResult result) {
        return result != null && result.semanticGrade() != null
                && (result.semanticGrade().safetyPass() || result.safetyAdjudication() != null);
    }

    private static void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Required source benchmark file is missing: " + path);
        }
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

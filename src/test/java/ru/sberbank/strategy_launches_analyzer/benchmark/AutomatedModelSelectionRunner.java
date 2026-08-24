package ru.sberbank.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import ru.sberbank.strategy_launches_analyzer.service.agent.AgentModelClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

final class AutomatedModelSelectionRunner {
    static final String REPORT_SCHEMA_VERSION = "gigachat-model-selection/v1";
    private static final String MANIFEST_SCHEMA_VERSION = "gigachat-model-selection-manifest/v1";

    private final ObjectMapper objectMapper;
    private final AgentModelClient modelClient;
    private final AgentAnalysisPostProcessor postProcessor;
    private final BenchmarkInputFactory inputFactory;
    private final Function<String, SemanticJudge> judgeFactory;
    private final Supplier<List<String>> availableChatModels;

    AutomatedModelSelectionRunner(
            ObjectMapper objectMapper,
            AgentModelClient modelClient,
            AgentAnalysisPostProcessor postProcessor,
            BenchmarkInputFactory inputFactory,
            Function<String, SemanticJudge> judgeFactory,
            Supplier<List<String>> availableChatModels
    ) {
        this.objectMapper = objectMapper;
        this.modelClient = modelClient;
        this.postProcessor = postProcessor;
        this.inputFactory = inputFactory;
        this.judgeFactory = judgeFactory;
        this.availableChatModels = availableChatModels;
    }

    AutomatedModelSelectionReport run(AutomatedBenchmarkConfiguration configuration) {
        JudgeSelectionReport judgeSelection = readJudgeSelection(configuration.judgeSelectionReport());
        Path calibrationDataset = Path.of(judgeSelection.datasetPath());
        requireAcceptedCalibration(
                judgeSelection.selectedCalibration(), calibrationDataset, judgeSelection.selectedJudgeModel()
        );
        if (judgeSelection.alternateJudgeModel() != null) {
            requireAcceptedCalibration(
                    judgeSelection.alternateCalibration(), calibrationDataset, judgeSelection.alternateJudgeModel()
            );
        }

        List<String> candidates = configuration.candidateModels();
        if (configuration.resumeFrom() == null) {
            List<String> available = availableChatModels.get().stream().distinct().toList();
            List<String> unavailable = candidates.stream().filter(model -> !available.contains(model)).toList();
            if (!unavailable.isEmpty()) {
                throw new IllegalStateException("Configured candidate models are unavailable: " + unavailable);
            }
        }

        Path runDirectory = configuration.resumeFrom() == null
                ? configuration.outputBaseDirectory().resolve(BenchmarkResultStore.newRunId())
                : configuration.resumeFrom();
        initializeManifest(runDirectory, configuration, judgeSelection, candidates);

        String primaryJudge = judgeSelection.selectedJudgeModel();
        Path pilotDirectory = runDirectory.resolve("pilot");
        Path fullDirectory = runDirectory.resolve("full");
        Path shortlistPath = runDirectory.resolve("shortlist.json");
        BenchmarkSummary pilot;
        List<String> finalists;
        if (Files.isRegularFile(shortlistPath) || Files.isRegularFile(fullDirectory.resolve("manifest.json"))) {
            pilot = readSummary(pilotDirectory.resolve("summary.json"));
            finalists = readOrRecoverShortlist(shortlistPath, fullDirectory.resolve("manifest.json"));
        } else {
            pilot = benchmark(
                    candidates,
                    primaryJudge,
                    configuration.pilotRepetitions(),
                    configuration.shuffleSeed(),
                    pilotDirectory
            );
            finalists = pilot.models().stream()
                    .filter(ModelBenchmarkSummary::eligible)
                    .sorted(modelOrder())
                    .limit(configuration.finalistCount())
                    .map(ModelBenchmarkSummary::model)
                    .toList();
            writeShortlist(shortlistPath, finalists);
        }
        if (finalists.size() < 2) {
            return writeReport(runDirectory, new AutomatedModelSelectionReport(
                    REPORT_SCHEMA_VERSION, Instant.now(), "REVIEW_REQUIRED",
                    primaryJudge, judgeSelection.alternateJudgeModel(), candidates, finalists,
                    pilotDirectory.toString(), null, null,
                    pilot.winner(), null, null, null, null,
                    List.of("Pilot left fewer than two models that passed every quality gate.")
            ));
        }

        BenchmarkSummary primary = benchmark(
                finalists,
                primaryJudge,
                configuration.fullRepetitions(),
                configuration.shuffleSeed(),
                fullDirectory
        );

        BenchmarkSummary alternate = null;
        Path alternateDirectory = null;
        if (judgeSelection.alternateJudgeModel() != null) {
            String alternateJudge = judgeSelection.alternateJudgeModel();
            alternateDirectory = runDirectory.resolve("rejudge-" + JudgeSelectionRunner.safeFileName(alternateJudge));
            alternate = new SavedResponseRejudgeRunner(
                    objectMapper, judgeFactory.apply(alternateJudge), inputFactory
            ).run(fullDirectory, alternateJudge, alternateDirectory);
        }

        AutomatedModelSelectionReport decision = decide(
                configuration,
                judgeSelection,
                candidates,
                finalists,
                runDirectory,
                primary,
                alternate,
                alternateDirectory
        );
        return writeReport(runDirectory, decision);
    }

    private BenchmarkSummary benchmark(
            List<String> models,
            String judgeModel,
            int repetitions,
            long shuffleSeed,
            Path outputDirectory
    ) {
        return new BenchmarkRunner(objectMapper, modelClient, postProcessor, judgeFactory.apply(judgeModel), inputFactory)
                .run(new BenchmarkConfiguration(models, judgeModel, repetitions, 1, shuffleSeed, null),
                        outputDirectory, false);
    }

    static AutomatedModelSelectionReport decide(
            AutomatedBenchmarkConfiguration configuration,
            JudgeSelectionReport judgeSelection,
            List<String> candidates,
            List<String> finalists,
            Path runDirectory,
            BenchmarkSummary primary,
            BenchmarkSummary alternate,
            Path alternateDirectory
    ) {
        List<String> reasons = new ArrayList<>();
        String primaryWinner = primary.winner();
        String alternateWinner = alternate == null ? null : alternate.winner();
        Double primaryGap = semanticGap(primary, primaryWinner);
        Double alternateGap = semanticGap(alternate, alternateWinner);

        if (primaryWinner == null) {
            reasons.add("The primary judge did not select an eligible winner.");
        }
        if (alternate == null) {
            reasons.add("No complete alternate-judge rejudge is available.");
        } else if (alternateWinner == null) {
            reasons.add("The alternate judge did not select an eligible winner.");
        } else if (!alternateWinner.equals(primaryWinner)) {
            reasons.add("Primary and alternate judges selected different winners.");
        }
        requireWinnerQuality(primary, primaryWinner, "Primary", configuration.minimumSemanticGap(), primaryGap, reasons);
        requireWinnerQuality(alternate, alternateWinner, "Alternate", configuration.minimumSemanticGap(), alternateGap, reasons);

        String selected = reasons.isEmpty() ? primaryWinner : null;
        return new AutomatedModelSelectionReport(
                REPORT_SCHEMA_VERSION,
                Instant.now(),
                selected == null ? "REVIEW_REQUIRED" : "SELECTED",
                judgeSelection.selectedJudgeModel(),
                judgeSelection.alternateJudgeModel(),
                candidates,
                finalists,
                runDirectory.resolve("pilot").toString(),
                runDirectory.resolve("full").toString(),
                alternateDirectory == null ? null : alternateDirectory.toString(),
                primaryWinner,
                alternateWinner,
                primaryGap,
                alternateGap,
                selected,
                reasons
        );
    }

    private static void requireWinnerQuality(
            BenchmarkSummary summary,
            String winner,
            String label,
            double minimumGap,
            Double gap,
            List<String> reasons
    ) {
        if (summary == null || winner == null) {
            return;
        }
        ModelBenchmarkSummary winnerSummary = summary.models().stream()
                .filter(model -> model.model().equals(winner))
                .findFirst()
                .orElseThrow();
        if (!winnerSummary.eligible()) {
            reasons.add(label + " winner did not pass every quality gate.");
        }
        if (winnerSummary.safetyNeedsReviewCount() > 0) {
            reasons.add(label + " winner has unresolved NEEDS_REVIEW safety adjudications.");
        }
        if (gap == null || gap < minimumGap) {
            reasons.add(label + " semantic lead is below %.3f.".formatted(minimumGap));
        }
    }

    private static Double semanticGap(BenchmarkSummary summary, String winner) {
        if (summary == null || winner == null || summary.models().size() < 2) {
            return null;
        }
        double winnerMean = summary.models().stream()
                .filter(model -> model.model().equals(winner))
                .mapToDouble(ModelBenchmarkSummary::semanticMean)
                .findFirst()
                .orElseThrow();
        double runnerUp = summary.models().stream()
                .filter(model -> !model.model().equals(winner))
                .mapToDouble(ModelBenchmarkSummary::semanticMean)
                .max()
                .orElseThrow();
        return winnerMean - runnerUp;
    }

    private static Comparator<ModelBenchmarkSummary> modelOrder() {
        return Comparator.comparingDouble(ModelBenchmarkSummary::semanticMean).reversed()
                .thenComparingDouble(ModelBenchmarkSummary::guardrailCorrectionRate)
                .thenComparingLong(ModelBenchmarkSummary::p95LatencyMs)
                .thenComparingDouble(ModelBenchmarkSummary::averageOutputTokens)
                .thenComparing(ModelBenchmarkSummary::model);
    }

    private JudgeSelectionReport readJudgeSelection(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Judge selection report is missing: " + path
                    + ". Run the judge-selection profile first.");
        }
        try {
            JudgeSelectionReport report = objectMapper.readValue(path.toFile(), JudgeSelectionReport.class);
            if (!JudgeSelectionRunner.REPORT_SCHEMA_VERSION.equals(report.schemaVersion())) {
                throw new IllegalStateException("Unsupported judge selection report: " + report.schemaVersion());
            }
            if (report.selectedJudgeModel() == null) {
                throw new IllegalStateException("Judge selection report has no accepted judge.");
            }
            return report;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read judge selection report " + path, ex);
        }
    }

    private BenchmarkSummary readSummary(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Required pilot summary is missing: " + path);
        }
        try {
            return objectMapper.readValue(path.toFile(), BenchmarkSummary.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read benchmark summary " + path, ex);
        }
    }

    private List<String> readOrRecoverShortlist(Path shortlistPath, Path fullManifestPath) {
        try {
            if (Files.isRegularFile(shortlistPath)) {
                return List.copyOf(objectMapper.readValue(
                        shortlistPath.toFile(), new TypeReference<List<String>>() { }
                ));
            }
            if (!Files.isRegularFile(fullManifestPath)) {
                throw new IllegalStateException("Model-selection shortlist and full manifest are both missing.");
            }
            List<String> recovered = objectMapper.readValue(
                    fullManifestPath.toFile(), BenchmarkManifest.class
            ).models();
            writeShortlist(shortlistPath, recovered);
            return recovered;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read model-selection shortlist.", ex);
        }
    }

    private void writeShortlist(Path path, List<String> finalists) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), finalists);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write model-selection shortlist.", ex);
        }
    }

    private void requireAcceptedCalibration(
            JudgeCandidateCalibration calibration,
            Path dataset,
            String model
    ) {
        if (calibration == null || !calibration.accepted() || calibration.reportPath() == null) {
            throw new IllegalStateException("Accepted calibration is missing for judge " + model);
        }
        JudgeCalibrationGate.requireAccepted(
                objectMapper, Path.of(calibration.reportPath()), dataset, model
        );
    }

    private void initializeManifest(
            Path runDirectory,
            AutomatedBenchmarkConfiguration configuration,
            JudgeSelectionReport judgeSelection,
            List<String> candidates
    ) {
        AutomatedModelSelectionManifest requested = new AutomatedModelSelectionManifest(
                MANIFEST_SCHEMA_VERSION,
                judgeSelectionFingerprint(judgeSelection),
                BenchmarkHashes.datasetHash(BenchmarkCaseLoader.DEFAULT_DATASET),
                BenchmarkHashes.promptHash(),
                candidates,
                judgeSelection.selectedJudgeModel(),
                judgeSelection.alternateJudgeModel(),
                configuration.pilotRepetitions(),
                configuration.fullRepetitions(),
                configuration.finalistCount(),
                configuration.shuffleSeed(),
                configuration.minimumSemanticGap()
        );
        Path manifestPath = runDirectory.resolve("manifest.json");
        try {
            Files.createDirectories(runDirectory);
            if (Files.isRegularFile(manifestPath)) {
                AutomatedModelSelectionManifest existing = objectMapper.readValue(
                        manifestPath.toFile(), AutomatedModelSelectionManifest.class
                );
                existing.requireCompatible(requested);
            } else {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), requested);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize model-selection run " + runDirectory, ex);
        }
    }

    private static String judgeSelectionFingerprint(JudgeSelectionReport selection) {
        JudgeCandidateCalibration selected = selection.selectedCalibration();
        JudgeCandidateCalibration alternate = selection.alternateCalibration();
        String value = selection.datasetHash()
                + "\n" + selection.selectedJudgeModel()
                + "\n" + BenchmarkHashes.fileHash(Path.of(selected.reportPath()))
                + "\n" + (selection.alternateJudgeModel() == null ? "" : selection.alternateJudgeModel())
                + "\n" + (alternate == null ? "" : BenchmarkHashes.fileHash(Path.of(alternate.reportPath())));
        return BenchmarkHashes.textHash(value);
    }

    private AutomatedModelSelectionReport writeReport(
            Path runDirectory,
            AutomatedModelSelectionReport report
    ) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(runDirectory.resolve("decision.json").toFile(), report);
            return report;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write model-selection decision.", ex);
        }
    }
}

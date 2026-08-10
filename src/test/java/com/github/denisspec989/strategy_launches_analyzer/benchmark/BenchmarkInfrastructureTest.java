package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DeterministicSeverityCalculator;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.DefaultAgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkInfrastructureTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void offlineRunnerLoadsAllCasesGradesCandidatesAndSelectsQualityWinner() {
        StrategyContractRegistry registry = new StrategyContractRegistry(new OpenApiStrategyContractLoader());
        BenchmarkInputFactory inputFactory = new BenchmarkInputFactory(
                new StrategyDiffEngine(new ContractValidator()), registry
        );
        Map<String, AgentAnalysisInput> inputsByCase = new HashMap<>();
        AgentModelClient client = (input, options) -> {
            AgentAnalysisInput firstInput = inputsByCase.putIfAbsent(input.metadata().requestId(), input);
            if (firstInput != null) {
                assertThat(input).isSameAs(firstInput);
            }
            return new AgentModelCallResult(
                    validRawAnalysis(input),
                    new TokenUsage(100, options.model().equals("model-a") ? 10 : 20, 110, 0L, 0L, options.model()),
                    options.model(),
                    options.model(),
                    options.model().equals("model-a") ? 100 : 200
            );
        };
        SemanticJudge judge = (input, analysis, expectations) -> {
            boolean deliberatelyBad = analysis.recommendations().stream()
                    .anyMatch(value -> value.contains("Ничего не проверять"));
            double score = deliberatelyBad ? 0.20 : 0.90;
            return new SemanticGrade(score, score, score, score, score, score, List.of())
                    .validatedAndReweighted();
        };
        BenchmarkRunner runner = new BenchmarkRunner(
                objectMapper,
                client,
                new DefaultAgentAnalysisPostProcessor(),
                judge,
                inputFactory
        );

        BenchmarkSummary summary = runner.run(new BenchmarkConfiguration(
                List.of("model-a", "model-b"), "fake-judge", 1, 1, 42, null
        ));

        assertThat(summary.winner()).isEqualTo("model-a");
        assertThat(summary.models()).hasSize(2).allMatch(ModelBenchmarkSummary::eligible);
        assertThat(inputsByCase).hasSize(21);
        assertThat(new BenchmarkCaseLoader(objectMapper).load(BenchmarkCaseLoader.DEFAULT_DATASET)).hasSize(21);
    }

    @Test
    void resumeRejectsIncompatibleManifest(@TempDir Path directory) throws Exception {
        BenchmarkManifest existing = manifest("prompt-a", "dataset-a");
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(directory.resolve("manifest.json").toFile(), existing);
        BenchmarkConfiguration configuration = new BenchmarkConfiguration(
                List.of("a", "b"), "judge", 1, 1, 42, directory
        );

        assertThatThrownBy(() -> BenchmarkResultStore.open(
                objectMapper, configuration, manifest("prompt-b", "dataset-a")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incompatible");
    }

    @Test
    void resumeRoundTripsJsonlResultsAndPreparesFailureDirectory(@TempDir Path directory) throws Exception {
        BenchmarkManifest manifest = manifest("prompt", "dataset");
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(directory.resolve("manifest.json").toFile(), manifest);
        BenchmarkResultStore store = BenchmarkResultStore.open(
                objectMapper,
                new BenchmarkConfiguration(List.of("a", "b"), "judge", 1, 1, 42, directory),
                manifest
        );
        StructuredAgentAnalysis raw = new StructuredAgentAnalysis(
                Severity.INFO,
                "Различий нет.",
                "Влияние отсутствует.",
                "Критических рисков нет.",
                List.of("Проверить результат."),
                List.of()
        );
        AgentModelCallResult call = new AgentModelCallResult(
                raw, new TokenUsage(10, 5, 15, 0L, 0L, "a"), "a", "a", 25
        );
        BenchmarkSampleResult success = new BenchmarkSampleResult(
                "case-success", List.of(), "a", 1, BenchmarkSampleStatus.SUCCESS,
                call, new DeterministicGrade(true, List.of(), 0, 0, 1.0),
                null, List.of(), null,
                new SemanticGrade(0.9, 0.9, 0.9, 0.9, 0.9, 0.9, List.of()), null
        );

        store.append(success);
        store.append(new BenchmarkSampleResult(
                "case-failure", List.of(), "b", 1, BenchmarkSampleStatus.CALL_FAILED,
                null, null, null, List.of(), null, null, "unavailable"
        ));
        new BenchmarkReportWriter(objectMapper).write(
                directory,
                new BenchmarkSummary("run", true, null, List.of("incomplete"), List.of(), List.of("synthetic"))
        );

        assertThat(store.readLatestResults().get(success.key()).callResult()).isEqualTo(call);
        assertThat(directory.resolve("failures")).isDirectory();
        assertThat(directory.resolve("results.jsonl")).isRegularFile();
        assertThat(directory.resolve("summary.json")).isRegularFile();
        assertThat(java.nio.file.Files.readString(directory.resolve("summary.csv")))
                .contains("avg_cache_read_input_tokens");
        assertThat(directory.resolve("summary.md")).isRegularFile();
    }

    private static BenchmarkManifest manifest(String promptHash, String datasetHash) {
        return new BenchmarkManifest(
                "run", Instant.EPOCH, "commit", "v1", promptHash, datasetHash,
                21, List.of("a", "b"), 1, 1, "judge", 42
        );
    }

    private static StructuredAgentAnalysis validRawAnalysis(AgentAnalysisInput input) {
        List<DiffExplanation> explanations = input.diffs().stream()
                .map(diff -> new DiffExplanation(
                        diff.id(),
                        diff.path(),
                        DeterministicSeverityCalculator.isHardCriticalDiff(diff)
                                ? Severity.CRITICAL
                                : Severity.WARNING,
                        "Обнаружено изменение; требуется предметная проверка."
                ))
                .toList();
        Severity severity = input.summary().deterministicSeverity();
        return new StructuredAgentAnalysis(
                severity,
                input.diffs().isEmpty() ? "Отличий между запусками нет." : "Обнаружены отличия между запусками.",
                input.diffs().isEmpty()
                        ? "Влияние на бизнес-решения отсутствует."
                        : "Изменения могут повлиять на интерпретацию результата.",
                severity == Severity.CRITICAL
                        ? "Обнаружены критические технические риски."
                        : "Критические технические риски не обнаружены.",
                List.of("Проверить результат перед принятием решения."),
                explanations
        );
    }
}

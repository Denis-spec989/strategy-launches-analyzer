package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrection;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrectionType;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BenchmarkAggregatorTest {
    private final BenchmarkAggregator aggregator = new BenchmarkAggregator();

    @Test
    void semanticScoreUsesConfiguredQualityWeights() {
        SemanticGrade grade = new SemanticGrade(1.0, 0.8, 0.6, 0.4, 0.2, 0.0, List.of())
                .validatedAndReweighted();

        assertThat(grade.overallScore()).isCloseTo(0.70, within(1e-9));
    }

    @Test
    void qualityTieUsesCorrectionsLatencyTokensAndModelIdInOrder() {
        List<String> models = List.of("model-a", "model-b", "model-c", "model-d", "model-e");
        BenchmarkSummary summary = aggregator.summarize("run", models, List.of(
                success("model-a", 0.90, 0, 100, 20),
                success("model-b", 0.91, 1, 10, 5),
                success("model-c", 0.90, 0, 50, 30),
                success("model-d", 0.90, 0, 50, 10),
                success("model-e", 0.90, 0, 50, 10)
        ), 1);

        assertThat(summary.winner()).isEqualTo("model-d");
        assertThat(summary.incomplete()).isFalse();
    }

    @Test
    void unavailableModelDoesNotStopOthersButPreventsSelectionWithOnlyOneCompleteCandidate() {
        BenchmarkSummary summary = aggregator.summarize("run", List.of("available", "missing"), List.of(
                success("available", 0.90, 0, 10, 10),
                failure("missing", BenchmarkSampleStatus.CALL_FAILED, null)
        ), 1);

        assertThat(summary.winner()).isNull();
        assertThat(summary.globalIssues()).anyMatch(issue -> issue.contains("Fewer than two"));
        assertThat(summary.models()).filteredOn(model -> model.model().equals("missing"))
                .allMatch(model -> !model.eligible());
    }

    @Test
    void judgeFailureDisablesWinnerEvenWhenCandidateCallsSucceeded() {
        AgentModelCallResult call = call("model-a", 10, 10);
        BenchmarkSummary summary = aggregator.summarize("run", List.of("model-a", "model-b"), List.of(
                failure("model-a", BenchmarkSampleStatus.JUDGE_FAILED, call),
                failure("model-b", BenchmarkSampleStatus.JUDGE_FAILED, call("model-b", 10, 10))
        ), 1);

        assertThat(summary.winner()).isNull();
        assertThat(summary.globalIssues()).anyMatch(issue -> issue.contains("judge call failed"));
    }

    private static BenchmarkSampleResult success(
            String model,
            double semanticScore,
            int corrections,
            long latencyMs,
            int outputTokens
    ) {
        DeterministicGrade pass = new DeterministicGrade(true, List.of(), 0, 0, 1.0);
        List<GuardrailCorrection> correctionList = corrections == 0
                ? List.of()
                : List.of(new GuardrailCorrection(
                        GuardrailCorrectionType.OVERALL_SEVERITY_CHANGED, null, "corrected"
                ));
        SemanticGrade grade = new SemanticGrade(
                semanticScore, semanticScore, semanticScore, semanticScore, semanticScore,
                semanticScore, List.of()
        ).validatedAndReweighted();
        return new BenchmarkSampleResult(
                "case", List.of(), model, 1, BenchmarkSampleStatus.SUCCESS,
                call(model, latencyMs, outputTokens), pass, finalAnalysis(), correctionList, pass, grade, null
        );
    }

    private static BenchmarkSampleResult failure(
            String model,
            BenchmarkSampleStatus status,
            AgentModelCallResult call
    ) {
        DeterministicGrade pass = new DeterministicGrade(true, List.of(), 0, 0, 1.0);
        return new BenchmarkSampleResult(
                "case", List.of(), model, 1, status, call,
                call == null ? null : pass,
                call == null ? null : finalAnalysis(),
                List.of(),
                call == null ? null : pass,
                null,
                "expected failure"
        );
    }

    private static AgentModelCallResult call(String model, long latencyMs, int outputTokens) {
        StructuredAgentAnalysis raw = new StructuredAgentAnalysis(
                Severity.INFO, "Итог", "Влияние", "Риски", List.of("Проверить"), List.of()
        );
        return new AgentModelCallResult(
                raw,
                new TokenUsage(100, outputTokens, 100 + outputTokens, 0L, 0L, model),
                model,
                model,
                latencyMs
        );
    }

    private static AgentAnalysis finalAnalysis() {
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                Severity.INFO,
                "Итог",
                "Влияние",
                "Риски",
                List.of("Проверить"),
                List.of(),
                TokenUsage.zero(),
                null
        );
    }
}

package com.github.denisspec989.strategy_launches_analyzer.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentAnalysis(
        AgentAnalysisStatus status,
        Severity overallSeverity,
        String summary,
        String businessImpact,
        String technicalRisks,
        List<String> recommendations,
        List<DiffExplanation> diffExplanations,
        TokenUsage tokenUsage,
        String errorMessage
) {
    public static AgentAnalysis failed(String errorMessage) {
        return new AgentAnalysis(
                AgentAnalysisStatus.FAILED,
                Severity.WARNING,
                "Agent analysis is unavailable. Deterministic diffs are still returned.",
                "",
                "LLM analysis failed and should be retried after checking model configuration.",
                List.of("Review deterministic diffs manually."),
                List.of(),
                TokenUsage.zero(),
                errorMessage
        );
    }
}

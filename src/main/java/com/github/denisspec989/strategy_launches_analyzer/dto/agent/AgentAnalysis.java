package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;

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
}

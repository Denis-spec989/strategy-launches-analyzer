package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;

import java.util.List;

public record StructuredAgentAnalysis(
        Severity overallSeverity,
        String summary,
        String businessImpact,
        String technicalRisks,
        List<String> recommendations,
        List<DiffExplanation> diffExplanations
) {
}

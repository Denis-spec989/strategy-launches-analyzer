package com.github.denisspec989.strategy_launches_analyzer.api;

import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.domain.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.domain.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffEntry;

import java.util.List;

public record CompareStrategyResponse(
        String strategyName,
        ComparisonSummary summary,
        List<DiffEntry> diffs,
        List<ContractIssue> contractValidation,
        AgentAnalysis agentAnalysis
) {
}

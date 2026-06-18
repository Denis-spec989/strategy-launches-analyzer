package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareStrategyResponse(
        String strategyName,
        String contractVersion,
        Instant analyzedAt,
        ComparisonSummary summary,
        List<DiffEntry> diffs,
        List<ContractIssue> contractValidation,
        AgentAnalysis agentAnalysis
) {
}

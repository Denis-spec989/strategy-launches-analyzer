package com.github.denisspec989.strategy_launches_analyzer.agent;

import com.github.denisspec989.strategy_launches_analyzer.api.LaunchMetadata;
import com.github.denisspec989.strategy_launches_analyzer.domain.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.domain.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffEntry;

import java.util.List;

public record AgentAnalysisInput(
        String strategyName,
        ComparisonSummary summary,
        List<DiffEntry> diffs,
        List<ContractIssue> contractValidation,
        List<ContractFieldContext> contractContext,
        LaunchMetadata metadata
) {
}

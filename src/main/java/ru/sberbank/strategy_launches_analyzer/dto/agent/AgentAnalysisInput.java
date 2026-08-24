package ru.sberbank.strategy_launches_analyzer.dto.agent;

import ru.sberbank.strategy_launches_analyzer.dto.api.LaunchMetadata;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffEntry;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;

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

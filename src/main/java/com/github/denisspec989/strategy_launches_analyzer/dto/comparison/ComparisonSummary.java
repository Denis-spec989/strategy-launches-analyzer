package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

public record ComparisonSummary(
        String strategyName,
        int totalDiffs,
        int metricDiffs,
        int modelDiffs,
        int contractTechnicalDiffs,
        int contractValidationIssues,
        boolean hasCriticalIssues,
        Severity deterministicSeverity
) {
    public static ComparisonSummary from(String strategyName, List<DiffEntry> diffs, List<ContractIssue> issues) {
        int metricDiffs = countByCategory(diffs, DiffCategory.METRIC);
        int modelDiffs = countByCategory(diffs, DiffCategory.MODEL);
        int contractTechnicalDiffs = countByCategory(diffs, DiffCategory.CONTRACT_TECHNICAL);
        Severity deterministicSeverity = DeterministicSeverityCalculator.calculate(diffs, issues);
        boolean hasCriticalIssues = deterministicSeverity == Severity.CRITICAL;

        return new ComparisonSummary(
                strategyName,
                diffs.size(),
                metricDiffs,
                modelDiffs,
                contractTechnicalDiffs,
                issues.size(),
                hasCriticalIssues,
                deterministicSeverity
        );
    }

    private static int countByCategory(List<DiffEntry> diffs, DiffCategory category) {
        return (int) diffs.stream()
                .filter(diff -> diff.category() == category)
                .count();
    }
}

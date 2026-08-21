package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

public record ComparisonSummary(
        int totalDiffs,
        int metricDiffs,
        int modelDiffs,
        int calculationContextDiffs,
        int contractTechnicalDiffs,
        int contractValidationIssues,
        boolean hasCriticalIssues,
        Severity deterministicSeverity
) {
    public static ComparisonSummary from(List<DiffEntry> diffs, List<ContractIssue> issues) {
        int metricDiffs = countByCategory(diffs, DiffCategory.METRIC);
        int modelDiffs = countByCategory(diffs, DiffCategory.MODEL);
        int calculationContextDiffs = countByCategory(diffs, DiffCategory.CALCULATION_CONTEXT);
        int contractTechnicalDiffs = countByCategory(diffs, DiffCategory.CONTRACT_TECHNICAL);
        Severity deterministicSeverity = DeterministicSeverityCalculator.calculate(diffs, issues);
        boolean hasCriticalIssues = deterministicSeverity == Severity.CRITICAL;

        return new ComparisonSummary(
                diffs.size(),
                metricDiffs,
                modelDiffs,
                calculationContextDiffs,
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

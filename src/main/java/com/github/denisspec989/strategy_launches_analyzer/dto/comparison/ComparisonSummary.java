package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

public record ComparisonSummary(
        @JsonProperty(required = true)
        int totalDiffs,
        @JsonProperty(required = true)
        int metricDiffs,
        @JsonProperty(required = true)
        int modelDiffs,
        @JsonProperty(required = true)
        int calculationContextDiffs,
        @JsonProperty(required = true)
        int contractTechnicalDiffs,
        @JsonProperty(required = true)
        int contractValidationIssues,
        @JsonProperty(required = true)
        boolean hasCriticalIssues,
        @JsonProperty(required = true)
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

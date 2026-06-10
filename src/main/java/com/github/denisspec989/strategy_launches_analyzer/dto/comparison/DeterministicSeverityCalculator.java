package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

public final class DeterministicSeverityCalculator {
    private DeterministicSeverityCalculator() {
    }

    public static Severity calculate(List<DiffEntry> diffs, List<ContractIssue> issues) {
        List<DiffEntry> safeDiffs = diffs == null ? List.of() : diffs;
        List<ContractIssue> safeIssues = issues == null ? List.of() : issues;
        if (hasCriticalSignal(safeDiffs, safeIssues)) {
            return Severity.CRITICAL;
        }
        if (safeDiffs.isEmpty() && safeIssues.isEmpty()) {
            return Severity.INFO;
        }
        return Severity.WARNING;
    }

    public static boolean hasCriticalSignal(List<DiffEntry> diffs, List<ContractIssue> issues) {
        List<DiffEntry> safeDiffs = diffs == null ? List.of() : diffs;
        List<ContractIssue> safeIssues = issues == null ? List.of() : issues;
        return safeIssues.stream().anyMatch(issue -> issue.severity() == Severity.CRITICAL)
                || safeDiffs.stream().anyMatch(DeterministicSeverityCalculator::isHardCriticalDiff);
    }

    public static boolean isHardCriticalDiff(DiffEntry diff) {
        if (diff == null) {
            return false;
        }
        boolean hardCriticalType = diff.type() == DiffType.TYPE_MISMATCH
                || diff.type() == DiffType.NULLABILITY_VIOLATION
                || diff.type() == DiffType.REQUIRED_FIELD_MISSING;
        String path = diff.path() == null ? "" : diff.path();
        return hardCriticalType || path.endsWith(".mode") || path.endsWith(".type");
    }
}

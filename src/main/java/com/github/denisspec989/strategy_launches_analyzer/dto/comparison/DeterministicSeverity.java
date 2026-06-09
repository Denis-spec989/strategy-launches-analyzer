package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

public final class DeterministicSeverity {
    private DeterministicSeverity() {
    }

    public static Severity from(List<DiffEntry> diffs, List<ContractIssue> issues) {
        if (hasCriticalSignal(diffs, issues)) {
            return Severity.CRITICAL;
        }
        return diffs.isEmpty() && issues.isEmpty() ? Severity.INFO : Severity.WARNING;
    }

    public static boolean hasCriticalSignal(List<DiffEntry> diffs, List<ContractIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.CRITICAL)
                || diffs.stream().anyMatch(DeterministicSeverity::isHardCriticalDiff);
    }

    public static boolean isHardCriticalDiff(DiffEntry diff) {
        String path = diff.path() == null ? "" : diff.path();
        return diff.type() == DiffType.TYPE_MISMATCH
                || diff.type() == DiffType.NULLABILITY_VIOLATION
                || diff.type() == DiffType.REQUIRED_FIELD_MISSING
                || path.endsWith(".mode")
                || path.endsWith(".type");
    }
}

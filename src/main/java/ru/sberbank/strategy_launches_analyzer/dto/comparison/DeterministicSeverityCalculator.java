package ru.sberbank.strategy_launches_analyzer.dto.comparison;

import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;

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
                || safeDiffs.stream().anyMatch(diff -> diff.deterministicSeverity() == Severity.CRITICAL);
    }

    /**
     * Resolves the single severity floor for an individual diff. Contract issues are associated
     * only by exact path equality so similarly named or nested fields cannot escalate each other.
     */
    public static Severity resolveDiffSeverity(DiffType type, String path, List<ContractIssue> issues) {
        if (isHardCriticalDiff(type, path)) {
            return Severity.CRITICAL;
        }
        List<ContractIssue> safeIssues = issues == null ? List.of() : issues;
        boolean hasCriticalIssueAtSamePath = safeIssues.stream()
                .anyMatch(issue -> issue != null
                        && issue.severity() == Severity.CRITICAL
                        && java.util.Objects.equals(issue.path(), path));
        return hasCriticalIssueAtSamePath ? Severity.CRITICAL : Severity.WARNING;
    }

    private static boolean isHardCriticalDiff(DiffType type, String path) {
        boolean hardCriticalType = type == DiffType.TYPE_MISMATCH
                || type == DiffType.NULLABILITY_VIOLATION
                || type == DiffType.REQUIRED_FIELD_MISSING;
        String safePath = path == null ? "" : path;
        return hardCriticalType || safePath.endsWith(".mode") || safePath.endsWith(".type");
    }
}

package ru.sberbank.strategy_launches_analyzer.dto.comparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssueType;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffCategory;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffEntry;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffType;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.LaunchSide;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.sberbank.strategy_launches_analyzer.TestIds.D001;
import static ru.sberbank.strategy_launches_analyzer.TestIds.D002;
import static ru.sberbank.strategy_launches_analyzer.TestIds.D003;
import static ru.sberbank.strategy_launches_analyzer.TestIds.D004;

class ComparisonSummaryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emptyComparisonUsesInfoDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from(List.of(), List.of());

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.INFO);
    }

    @Test
    void comparisonWithDiffUsesWarningDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from(List.of(metricDiff()), List.of());

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void comparisonWithCriticalIssueUsesCriticalDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from(List.of(), List.of(criticalIssue()));

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(summary.hasCriticalIssues()).isTrue();
    }

    @Test
    void comparisonWithHardCriticalDiffUsesCriticalDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from(List.of(hardCriticalDiff()), List.of());

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(summary.hasCriticalIssues()).isTrue();
    }

    @Test
    void serializesDeterministicSeverityWithoutHighestSeverity() throws Exception {
        String json = objectMapper.writeValueAsString(ComparisonSummary.from(List.of(metricDiff()), List.of()));

        assertThat(json).contains("\"deterministicSeverity\":\"WARNING\"");
        assertThat(json).doesNotContain("highestSeverity");
        assertThat(json).doesNotContain("strategyName");
    }

    @Test
    void categoryBreakdownSumsToTotalDiffsIncludingCalculationContext() {
        ComparisonSummary summary = ComparisonSummary.from(
                List.of(metricDiff(), modelDiff(), calculationContextDiff(), contractTechnicalDiff()),
                List.of()
        );

        assertThat(summary.calculationContextDiffs()).isEqualTo(1);
        assertThat(summary.metricDiffs()
                + summary.modelDiffs()
                + summary.calculationContextDiffs()
                + summary.contractTechnicalDiffs())
                .isEqualTo(summary.totalDiffs());
    }

    private static DiffEntry modelDiff() {
        return new DiffEntry(
                D002,
                "strategyResponse.lgdData.lgdModel",
                DiffType.STRING_VALUE_CHANGED,
                DiffCategory.MODEL,
                null,
                null,
                null,
                null,
                Severity.WARNING
        );
    }

    private static DiffEntry calculationContextDiff() {
        return new DiffEntry(
                D003,
                "strategyResponse.lgdData.scenario",
                DiffType.STRING_VALUE_CHANGED,
                DiffCategory.CALCULATION_CONTEXT,
                null,
                null,
                null,
                null,
                Severity.WARNING
        );
    }

    private static DiffEntry contractTechnicalDiff() {
        return new DiffEntry(
                D004,
                "strategyResponse.extra",
                DiffType.FIELD_ADDED_IN_SHADOW,
                DiffCategory.CONTRACT_TECHNICAL,
                null,
                null,
                null,
                null,
                Severity.WARNING
        );
    }

    private static DiffEntry metricDiff() {
        return new DiffEntry(
                D001,
                "strategyResponse.lgdData.lgd",
                DiffType.NUMERIC_VALUE_CHANGED,
                DiffCategory.METRIC,
                null,
                null,
                null,
                null,
                Severity.WARNING
        );
    }

    private static DiffEntry hardCriticalDiff() {
        return new DiffEntry(
                D001,
                "strategyResponse.lgdData.mode",
                DiffType.STRING_VALUE_CHANGED,
                DiffCategory.CONTRACT_TECHNICAL,
                null,
                null,
                null,
                null,
                Severity.CRITICAL
        );
    }

    private static ContractIssue criticalIssue() {
        return new ContractIssue(
                "C001",
                LaunchSide.SHADOW,
                "strategyResponse.lgdData.lgd",
                ContractIssueType.REQUIRED_FIELD_MISSING,
                Severity.CRITICAL,
                "number, 1..1",
                "missing",
                null,
                "Required field is missing."
        );
    }
}

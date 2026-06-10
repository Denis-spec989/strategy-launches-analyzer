package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonSummaryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emptyComparisonUsesInfoDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from("LGD_DIGITAL", List.of(), List.of());

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.INFO);
    }

    @Test
    void comparisonWithDiffUsesWarningDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from("LGD_DIGITAL", List.of(metricDiff()), List.of());

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void comparisonWithCriticalIssueUsesCriticalDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from("LGD_DIGITAL", List.of(), List.of(criticalIssue()));

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(summary.hasCriticalIssues()).isTrue();
    }

    @Test
    void comparisonWithHardCriticalDiffUsesCriticalDeterministicSeverity() {
        ComparisonSummary summary = ComparisonSummary.from("LGD_DIGITAL", List.of(hardCriticalDiff()), List.of());

        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(summary.hasCriticalIssues()).isTrue();
    }

    @Test
    void serializesDeterministicSeverityWithoutHighestSeverity() throws Exception {
        String json = objectMapper.writeValueAsString(ComparisonSummary.from("LGD_DIGITAL", List.of(metricDiff()), List.of()));

        assertThat(json).contains("\"deterministicSeverity\":\"WARNING\"");
        assertThat(json).doesNotContain("highestSeverity");
    }

    private static DiffEntry metricDiff() {
        return new DiffEntry(
                "D001",
                "strategyResponse.lgdData.lgd",
                DiffType.NUMERIC_VALUE_CHANGED,
                DiffCategory.METRIC,
                null,
                null,
                null,
                null,
                "Numeric value changed in shadow launch."
        );
    }

    private static DiffEntry hardCriticalDiff() {
        return new DiffEntry(
                "D001",
                "strategyResponse.lgdData.mode",
                DiffType.STRING_VALUE_CHANGED,
                DiffCategory.CONTRACT_TECHNICAL,
                null,
                null,
                null,
                null,
                "Technical field changed in shadow launch."
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

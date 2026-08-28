package ru.sberbank.strategy_launches_analyzer.service.batch;

import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemStatus;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BatchReportStatistics {
    private int inputItems;
    private int completedItems;
    private int failedItems;
    private int unchangedItems;
    private int reportedItems;
    private long totalDiffs;
    private long totalContractValidationIssues;
    private final Map<Severity, Integer> severityCounts = new LinkedHashMap<>();

    public BatchReportStatistics() {
        for (Severity severity : Severity.values()) {
            severityCounts.put(severity, 0);
        }
    }

    public void accept(BatchItemResult result) {
        inputItems++;
        if (result.status() == BatchItemStatus.COMPLETED) {
            completedItems++;
            if (!isIncludedInHumanReadableReport(result)) {
                unchangedItems++;
                return;
            }
            Severity severity = result.response().summary().deterministicSeverity();
            severityCounts.compute(severity, (key, count) -> count == null ? 1 : count + 1);
            totalDiffs += result.response().summary().totalDiffs();
            totalContractValidationIssues += result.response().summary().contractValidationIssues();
        } else {
            failedItems++;
        }
        reportedItems++;
    }

    public static boolean isIncludedInHumanReadableReport(BatchItemResult result) {
        if (result.status() == BatchItemStatus.FAILED) {
            return true;
        }
        return result.response().summary().totalDiffs() > 0
                || result.response().summary().contractValidationIssues() > 0;
    }

    public int inputItems() {
        return inputItems;
    }

    public int completedItems() {
        return completedItems;
    }

    public int failedItems() {
        return failedItems;
    }

    public int unchangedItems() {
        return unchangedItems;
    }

    public int reportedItems() {
        return reportedItems;
    }

    public long totalDiffs() {
        return totalDiffs;
    }

    public long totalContractValidationIssues() {
        return totalContractValidationIssues;
    }

    public Map<String, Integer> severityCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        severityCounts.forEach((severity, count) -> result.put(severity.name(), count));
        return result;
    }
}

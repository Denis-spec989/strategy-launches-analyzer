package com.github.denisspec989.strategy_launches_analyzer.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.JsonValueSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractIssue(
        String id,
        LaunchSide side,
        String path,
        ContractIssueType type,
        Severity severity,
        String expected,
        String actual,
        JsonNode actualValue,
        JsonValueSummary actualValueSummary,
        String message
) {
    public ContractIssue(
            String id,
            LaunchSide side,
            String path,
            ContractIssueType type,
            Severity severity,
            String expected,
            String actual,
            JsonNode actualValue,
            String message
    ) {
        this(id, side, path, type, severity, expected, actual, actualValue, null, message);
    }
}

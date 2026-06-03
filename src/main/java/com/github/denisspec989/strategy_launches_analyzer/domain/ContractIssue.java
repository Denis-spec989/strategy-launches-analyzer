package com.github.denisspec989.strategy_launches_analyzer.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

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
        String message
) {
}

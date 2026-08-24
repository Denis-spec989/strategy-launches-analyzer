package com.github.denisspec989.strategy_launches_analyzer.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractIssue(
        @JsonProperty(required = true)
        String id,
        @JsonProperty(required = true)
        LaunchSide side,
        @JsonProperty(required = true)
        String path,
        @JsonProperty(required = true)
        ContractIssueType type,
        @JsonProperty(required = true)
        Severity severity,
        @JsonProperty(required = true)
        String expected,
        @JsonProperty(required = true)
        String actual,
        JsonNode actualValue,
        @JsonProperty(required = true)
        String message
) {
}

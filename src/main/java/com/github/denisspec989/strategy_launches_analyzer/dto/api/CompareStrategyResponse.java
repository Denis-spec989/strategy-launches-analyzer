package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareStrategyResponse(
        @JsonProperty(required = true)
        String strategyName,
        @JsonProperty(required = true)
        String contractVersion,
        @JsonProperty(required = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant analyzedAt,
        @JsonProperty(required = true)
        LaunchMetadata metadata,
        @JsonProperty(required = true)
        ComparisonSummary summary,
        @JsonProperty(required = true)
        List<DiffEntry> diffs,
        @JsonProperty(required = true)
        List<ContractIssue> contractValidation,
        @JsonProperty(required = true)
        AgentAnalysis agentAnalysis
) {
}

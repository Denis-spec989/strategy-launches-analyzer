package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;

public record DiffExplanation(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Identifier of the deterministic diff being explained; must match a diffId present in the payload.")
        String diffId,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Dotted path of the diff, copied verbatim from the payload diff.")
        String path,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Severity of this specific diff: INFO, WARNING, or CRITICAL.")
        Severity severity,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Explanation of the change in business and technical terms, in Russian.")
        String explanation
) {
}

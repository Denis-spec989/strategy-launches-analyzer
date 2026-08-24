package ru.sberbank.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FailedAgentAnalysis", description = "Safe public description of an LLM analysis failure.")
public record FailedAgentAnalysisSchema(
        @JsonProperty(required = true)
        @Schema(allowableValues = "FAILED")
        AgentAnalysisStatus status,
        @JsonProperty(required = true)
        AgentFallbackReason failureReason,
        @JsonProperty(required = true)
        String errorMessage
) {
}

package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FailedAgentAnalysis", description = "Safe public description of an LLM analysis failure.")
public record FailedAgentAnalysisSchema(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "FAILED")
        AgentAnalysisStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        AgentFallbackReason failureReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String errorMessage
) {
}

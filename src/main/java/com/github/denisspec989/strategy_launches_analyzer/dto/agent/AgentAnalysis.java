package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
        description = "LLM analysis. The response shape depends on status.",
        oneOf = {CompletedAgentAnalysisSchema.class, FailedAgentAnalysisSchema.class},
        discriminatorProperty = "status"
)
public record AgentAnalysis(
        @Schema(hidden = true)
        AgentAnalysisStatus status,
        @Schema(hidden = true)
        Severity overallSeverity,
        @Schema(hidden = true)
        String summary,
        @Schema(hidden = true)
        String businessImpact,
        @Schema(hidden = true)
        String technicalRisks,
        @Schema(hidden = true)
        List<String> recommendations,
        @Schema(hidden = true)
        List<DiffExplanation> diffExplanations,
        @Schema(hidden = true)
        AgentFallbackReason failureReason,
        @Schema(hidden = true)
        String errorMessage
) {
    public static AgentAnalysis failed(AgentFallbackReason reason) {
        AgentFallbackReason safeReason = reason == null ? AgentFallbackReason.INTERNAL : reason;
        return new AgentAnalysis(
                AgentAnalysisStatus.FAILED,
                null,
                null,
                null,
                null,
                null,
                null,
                safeReason,
                safeReason.publicMessage()
        );
    }
}

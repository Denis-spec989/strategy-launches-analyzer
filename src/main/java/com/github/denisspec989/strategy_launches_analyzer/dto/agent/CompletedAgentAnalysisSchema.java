package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "CompletedAgentAnalysis", description = "Successful LLM analysis.")
public record CompletedAgentAnalysisSchema(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "COMPLETED")
        AgentAnalysisStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Severity overallSeverity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String businessImpact,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String technicalRisks,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> recommendations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<DiffExplanation> diffExplanations
) {
}

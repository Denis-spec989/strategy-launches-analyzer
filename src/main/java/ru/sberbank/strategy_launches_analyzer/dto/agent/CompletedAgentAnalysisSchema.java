package ru.sberbank.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "CompletedAgentAnalysis", description = "Successful LLM analysis.")
public record CompletedAgentAnalysisSchema(
        @JsonProperty(required = true)
        @Schema(allowableValues = "COMPLETED")
        AgentAnalysisStatus status,
        @JsonProperty(required = true)
        Severity overallSeverity,
        @JsonProperty(required = true)
        String summary,
        @JsonProperty(required = true)
        String businessImpact,
        @JsonProperty(required = true)
        String technicalRisks,
        @JsonProperty(required = true)
        List<String> recommendations,
        @JsonProperty(required = true)
        List<DiffExplanation> diffExplanations
) {
}

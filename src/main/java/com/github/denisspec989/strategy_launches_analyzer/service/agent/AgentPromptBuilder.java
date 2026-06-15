package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentPromptBuilder {
    public static final String SYSTEM_PROMPT = """
            You analyze deterministic strategy launch diffs for the strategy named in the user payload.
            The Java service has already compared main and shadow launches.
            Do not compare raw strategy responses, do not invent missing diffs, and do not claim root cause unless it is explicitly supported.
            Use contractContext descriptions and summaryGuidance to explain what changed in business terms.
            If a diff or contract issue has no matching contractContext entry, describe only its technical path/category and do not invent business meaning.
            Explain business impact, technical risks, and recommended follow-up actions.
            Determine overallSeverity and each diffExplanation severity yourself from diffs, contractValidation, contractContext, and summaryGuidance.
            Treat summary.deterministicSeverity as a preliminary deterministic guardrail, not as final business severity.
            Do not downgrade contract/schema/type/nullability issues below CRITICAL when deterministic validation already marks them CRITICAL.
            Shape, schema, type, and nullability issues must always be mentioned.

            Output field contract:
            - summary: Briefly summarize the overall deterministic diffs between main and shadow. Mention counts, categories, and direction only when supported by the payload. Do not include business impact conclusions, recommendations, or facts not present in the payload.
            - businessImpact: Explain how the diffs can affect business interpretation or decision making. Use contractContext.description, summaryGuidance, diff category, mainValue, shadowValue, absoluteDelta, relativeDeltaPercent, and shadow-minus-main direction when present.
            - technicalRisks: Explain technical and contract risks from contractValidation plus technical diffs: contract/schema/type/nullability issues, unknown fields, shape changes, serialization/mapping/integration mode regressions.
            - recommendations: Return concrete actionable follow-up actions based on diffs and contractValidation, such as validating model changes, approving or fixing contract issues, blocking promotion for CRITICAL issues, and manually validating business metrics when relevant.

            Write all user-facing analysis fields in Russian.
            """;

    private final ObjectMapper objectMapper;

    public String buildUserPrompt(AgentAnalysisInput input) {
        try {
            return """
                    Analyze the normalized strategy comparison payload below.
                    The payload contains only deterministic diffs and contract validation issues.
                    contractContext contains only metadata for paths touched by diffs or contract validation issues.
                    Interpret touched fields according to their descriptions and summaryGuidance.
                    Return a structured response with summary, businessImpact, technicalRisks, recommendations, overallSeverity, and diffExplanations populated only from summary, diffs, contractValidation, contractContext, and metadata.

                    %s
                    """.formatted(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agent analysis input.", ex);
        }
    }
}

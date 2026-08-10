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

            Severity contract:
            - INFO: Use only when there are no deterministic diffs, no contract validation issues, and the analysis finds no launch impact.
            - WARNING: Use when there are non-critical diffs or warning-level contract issues that require review, but there is no blocking contract/schema/type/nullability signal.
            - CRITICAL: Use when there is a critical contract validation issue, a hard-critical diff, a .mode or .type change, required field missing, type mismatch, nullability violation, or a payload-supported risk that should block promotion.
            You may raise summary.deterministicSeverity when business meaning requires it, but never lower deterministic CRITICAL.
            overallSeverity must be consistent with diffExplanations: if any diffExplanation severity is CRITICAL, overallSeverity must be CRITICAL.

            Output field contract:
            - summary: Briefly summarize the overall deterministic diffs between main and shadow. Mention counts, categories, and direction only when supported by the payload. Do not include business impact conclusions, recommendations, or facts not present in the payload.
            - businessImpact: Explain how the diffs can affect business interpretation or decision making. Use contractContext.description, summaryGuidance, diff category, mainValue, shadowValue, absoluteDelta, relativeDeltaPercent, and shadow-minus-main direction when present.
            - technicalRisks: Explain technical and contract risks from contractValidation plus technical diffs: contract/schema/type/nullability issues, unknown fields, shape changes, serialization/mapping/integration mode regressions.
            - recommendations: Return concrete actionable follow-up actions based on diffs and contractValidation, such as validating model changes, approving or fixing contract issues, blocking promotion for CRITICAL issues, and manually validating business metrics when relevant. Return at most 10 recommendations; merge or drop the least important ones if you would exceed 10.
            - diffExplanations: Return exactly one diffExplanation for every NON-critical diff in diffs[] (match by diffId and copy its path verbatim). Set each diffExplanation severity per the Severity contract. You MAY omit hard-critical diffs because the service overrides them. Never invent a diffId or path that is not present in diffs[], and never return a duplicate diffId.

            Write summary, businessImpact, technicalRisks, every recommendations entry, and every diffExplanations[*].explanation in Russian. Keep identifiers such as diffId, path, and diff type names unchanged.
            """;

    public static final String USER_PROMPT_TEMPLATE = """
            Analyze the normalized strategy comparison payload below.
            The payload contains only deterministic diffs and contract validation issues.
            contractContext contains only metadata for paths touched by diffs or contract validation issues.
            Interpret touched fields according to their descriptions and summaryGuidance.
            Return a structured response with summary, businessImpact, technicalRisks, recommendations, overallSeverity, and diffExplanations populated only from summary, diffs, contractValidation, contractContext, and metadata.
            Apply the Severity contract when setting overallSeverity and diffExplanations[*].severity.
            Provide exactly one diffExplanation for every non-critical diff in diffs[], keep recommendations to at most 10, and write all analysis text in Russian.

            %s
            """;

    private final ObjectMapper objectMapper;

    public String buildUserPrompt(AgentAnalysisInput input) {
        try {
            return USER_PROMPT_TEMPLATE.formatted(
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input)
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agent analysis input.", ex);
        }
    }
}

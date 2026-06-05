package com.github.denisspec989.strategy_launches_analyzer.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AgentPromptBuilder {
    public static final String SYSTEM_PROMPT = """
            You analyze deterministic strategy launch diffs for LGD_DIGITAL.
            The Java service has already compared main and shadow launches.
            Do not compare raw strategy responses, do not invent missing diffs, and do not claim root cause unless it is explicitly supported.
            Use contractContext descriptions and summaryGuidance to explain what changed in business terms.
            If a diff or contract issue has no matching contractContext entry, describe only its technical path/category and do not invent business meaning.
            Explain business impact, technical risks, and recommended follow-up actions.
            Shape, schema, type, and nullability issues must always be mentioned.
            Write all user-facing analysis fields in Russian.
            """;

    private final ObjectMapper objectMapper;

    public AgentPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildUserPrompt(AgentAnalysisInput input) {
        try {
            return """
                    Analyze the normalized LGD_DIGITAL comparison payload below.
                    The payload contains only deterministic diffs and contract validation issues.
                    contractContext contains only metadata for paths touched by diffs or contract validation issues.
                    Interpret touched fields according to their descriptions and summaryGuidance.

                    %s
                    """.formatted(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(input));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agent analysis input.", ex);
        }
    }
}

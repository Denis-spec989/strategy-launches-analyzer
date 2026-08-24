package ru.sberbank.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;

import java.util.List;

@JsonClassDescription("Structured business analysis of deterministic strategy launch diffs. All textual fields must be written in Russian.")
public record StructuredAgentAnalysis(
        // Без @JsonPropertyDescription: Severity — enum, в JSON-схеме выносится в $defs/$ref,
        // а используемый строгий structured output не допускает sibling-ключевые слова рядом с $ref.
        // Семантику severity задаёт SYSTEM_PROMPT (раздел Severity contract) и значения самого enum.
        @JsonProperty(required = true)
        Severity overallSeverity,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Short summary of the deterministic diffs (counts, categories, direction), in Russian. No business conclusions or recommendations.")
        String summary,

        @JsonProperty(required = true)
        @JsonPropertyDescription("How the diffs affect business interpretation and the promotion decision, in Russian.")
        String businessImpact,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Technical and contract risks: schema/type/nullability issues, unknown fields, shape changes, in Russian.")
        String technicalRisks,

        @JsonProperty(required = true)
        @JsonPropertyDescription("At most 10 concrete, actionable follow-up actions, in Russian.")
        List<String> recommendations,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Exactly one explanation per deterministic diff. Match diffId and copy path verbatim from the payload; never invent ids or paths and never duplicate a diffId.")
        List<DiffExplanation> diffExplanations
) {
}

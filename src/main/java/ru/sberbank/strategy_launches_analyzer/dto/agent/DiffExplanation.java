package ru.sberbank.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;

import java.util.UUID;

public record DiffExplanation(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Identifier of the deterministic diff being explained; must match a diffId present in the payload.")
        UUID diffId,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Dotted path of the diff, copied verbatim from the payload diff.")
        String path,

        // Без @JsonPropertyDescription: Severity — enum ($ref в JSON-схеме), а строгий structured output
        // Формат провайдера не допускает sibling-ключевые слова рядом с $ref.
        // Семантику задаёт SYSTEM_PROMPT и значения enum.
        @JsonProperty(required = true)
        Severity severity,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Explanation of the change in business and technical terms, in Russian.")
        String explanation
) {
}

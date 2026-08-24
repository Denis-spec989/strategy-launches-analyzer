package ru.sberbank.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailCorrection(
        GuardrailCorrectionType type,
        UUID diffId,
        String detail
) {
}

package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailCorrection(
        GuardrailCorrectionType type,
        String diffId,
        String detail
) {
}

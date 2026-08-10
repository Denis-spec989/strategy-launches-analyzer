package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import java.util.List;

public record AgentPostProcessingResult(
        AgentAnalysis analysis,
        List<GuardrailCorrection> corrections
) {
    public AgentPostProcessingResult {
        corrections = corrections == null ? List.of() : List.copyOf(corrections);
    }
}

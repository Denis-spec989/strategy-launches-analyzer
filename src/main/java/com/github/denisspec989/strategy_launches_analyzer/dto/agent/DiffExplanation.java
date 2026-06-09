package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;

public record DiffExplanation(
        String diffId,
        String path,
        Severity severity,
        String explanation
) {
}

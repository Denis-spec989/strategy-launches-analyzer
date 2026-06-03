package com.github.denisspec989.strategy_launches_analyzer.domain;

public record DiffExplanation(
        String diffId,
        String path,
        Severity severity,
        String explanation
) {
}

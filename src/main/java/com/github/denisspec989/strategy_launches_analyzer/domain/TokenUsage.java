package com.github.denisspec989.strategy_launches_analyzer.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long cacheReadInputTokens,
        Long cacheWriteInputTokens,
        String model
) {
    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0, 0L, 0L, null);
    }
}

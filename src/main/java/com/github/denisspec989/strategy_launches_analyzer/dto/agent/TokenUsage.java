package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

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

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
                add(inputTokens, other.inputTokens),
                add(outputTokens, other.outputTokens),
                add(totalTokens, other.totalTokens),
                add(cacheReadInputTokens, other.cacheReadInputTokens),
                add(cacheWriteInputTokens, other.cacheWriteInputTokens),
                hasText(other.model) ? other.model : model
        );
    }

    public static TokenUsage sum(TokenUsage left, TokenUsage right) {
        return (left == null ? zero() : left).plus(right);
    }

    private static Integer add(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }

    private static Long add(Long left, Long right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0L : left) + (right == null ? 0L : right);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

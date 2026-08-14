package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;

public record GigaChatStructuredCompletionResult<T>(
        T entity,
        TokenUsage tokenUsage,
        String actualModel
) {
}

package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;

public record GigaChatStructuredCompletionResult<T>(
        T entity,
        TokenUsage tokenUsage,
        String actualModel
) {
}

package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

public enum AgentFallbackReason {
    CAPACITY("LLM-анализ не выполнен: превышен лимит параллельных вызовов."),
    TRANSPORT("LLM-анализ не выполнен: сервис модели недоступен."),
    RESPONSE_INVALID("LLM-анализ не выполнен: модель вернула некорректный ответ."),
    REPAIR_EXHAUSTED("LLM-анализ не выполнен: не удалось исправить некорректный ответ модели."),
    INTERNAL("LLM-анализ не выполнен: внутренняя ошибка обработки.");

    private final String publicMessage;

    AgentFallbackReason(String publicMessage) {
        this.publicMessage = publicMessage;
    }

    public String metricValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public String publicMessage() {
        return publicMessage;
    }
}

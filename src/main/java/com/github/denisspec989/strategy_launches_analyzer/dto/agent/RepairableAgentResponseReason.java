package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

public enum RepairableAgentResponseReason {
    NO_CHOICE,
    INCOMPLETE_RESPONSE,
    EMPTY_CONTENT,
    INVALID_JSON,
    CONTRACT_VIOLATION;

    public String metricValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

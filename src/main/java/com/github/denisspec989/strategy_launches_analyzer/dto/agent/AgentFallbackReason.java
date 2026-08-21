package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

public enum AgentFallbackReason {
    CAPACITY,
    TRANSPORT,
    RESPONSE_INVALID,
    REPAIR_EXHAUSTED,
    INTERNAL;

    public String metricValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

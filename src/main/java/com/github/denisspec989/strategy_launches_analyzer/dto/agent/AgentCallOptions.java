package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

public record AgentCallOptions(String model) {
    public AgentCallOptions {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required.");
        }
    }
}

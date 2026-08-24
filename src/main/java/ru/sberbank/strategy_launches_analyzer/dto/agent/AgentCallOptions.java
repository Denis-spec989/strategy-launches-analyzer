package ru.sberbank.strategy_launches_analyzer.dto.agent;

public record AgentCallOptions(
        String model,
        AgentRepairContext repairContext
) {
    public AgentCallOptions(String model) {
        this(model, null);
    }

    public AgentCallOptions {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required.");
        }
    }
}

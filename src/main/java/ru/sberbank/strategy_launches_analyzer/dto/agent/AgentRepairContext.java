package ru.sberbank.strategy_launches_analyzer.dto.agent;

import java.util.List;

public record AgentRepairContext(
        RepairableAgentResponseReason reason,
        List<String> violations,
        StructuredAgentAnalysis previousResponse
) {
    public AgentRepairContext {
        if (reason == null) {
            throw new IllegalArgumentException("reason is required.");
        }
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}

package ru.sberbank.strategy_launches_analyzer.exceptions;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentRepairContext;
import ru.sberbank.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;

import java.util.List;

public class RepairableAgentResponseException extends AgentAnalysisException {
    private final RepairableAgentResponseReason reason;
    private final List<String> violations;
    private final StructuredAgentAnalysis previousResponse;
    private final TokenUsage tokenUsage;

    public RepairableAgentResponseException(
            String message,
            RepairableAgentResponseReason reason,
            List<String> violations,
            StructuredAgentAnalysis previousResponse,
            TokenUsage tokenUsage
    ) {
        super(message);
        this.reason = requireReason(reason);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
        this.previousResponse = previousResponse;
        this.tokenUsage = tokenUsage == null ? TokenUsage.zero() : tokenUsage;
    }

    public RepairableAgentResponseException(
            String message,
            Throwable cause,
            RepairableAgentResponseReason reason,
            List<String> violations,
            StructuredAgentAnalysis previousResponse,
            TokenUsage tokenUsage
    ) {
        super(message, cause);
        this.reason = requireReason(reason);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
        this.previousResponse = previousResponse;
        this.tokenUsage = tokenUsage == null ? TokenUsage.zero() : tokenUsage;
    }

    public RepairableAgentResponseReason reason() {
        return reason;
    }

    public List<String> violations() {
        return violations;
    }

    public StructuredAgentAnalysis previousResponse() {
        return previousResponse;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public AgentRepairContext repairContext() {
        return new AgentRepairContext(reason, violations, previousResponse);
    }

    private static RepairableAgentResponseReason requireReason(RepairableAgentResponseReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason is required.");
        }
        return reason;
    }
}

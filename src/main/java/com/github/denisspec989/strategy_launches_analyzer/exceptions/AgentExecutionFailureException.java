package com.github.denisspec989.strategy_launches_analyzer.exceptions;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;

public class AgentExecutionFailureException extends AgentAnalysisException {
    private final AgentFallbackReason reason;
    private final TokenUsage tokenUsage;

    public AgentExecutionFailureException(
            String message,
            Throwable cause,
            AgentFallbackReason reason,
            TokenUsage tokenUsage
    ) {
        super(message, cause);
        if (reason == null) {
            throw new IllegalArgumentException("reason is required.");
        }
        this.reason = reason;
        this.tokenUsage = tokenUsage == null ? TokenUsage.zero() : tokenUsage;
    }

    public AgentFallbackReason reason() {
        return reason;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }
}

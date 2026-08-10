package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

public record AgentModelCallResult(
        StructuredAgentAnalysis rawAnalysis,
        TokenUsage tokenUsage,
        String requestedModel,
        String actualModel,
        long durationMs
) {
}

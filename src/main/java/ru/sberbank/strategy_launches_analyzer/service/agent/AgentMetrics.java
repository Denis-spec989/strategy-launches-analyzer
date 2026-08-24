package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.GuardrailCorrection;
import ru.sberbank.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;

import java.util.List;

public interface AgentMetrics {
    enum AnalysisOutcome {
        COMPLETED,
        REPAIRED;

        String metricValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    void recordAnalysis(
            String strategy,
            AnalysisOutcome outcome,
            long durationNanos,
            List<GuardrailCorrection> corrections,
            TokenUsage tokenUsage
    );

    void recordFallback(
            String strategy,
            AgentFallbackReason reason,
            long durationNanos,
            TokenUsage tokenUsage
    );

    void recordRepair(String strategy, RepairableAgentResponseReason reason, boolean success);

    void recordValidationFailure(String strategy, RepairableAgentResponseReason reason);

    static AgentMetrics noop() {
        return NoopAgentMetrics.INSTANCE;
    }

    final class NoopAgentMetrics implements AgentMetrics {
        private static final NoopAgentMetrics INSTANCE = new NoopAgentMetrics();

        private NoopAgentMetrics() {
        }

        @Override
        public void recordAnalysis(
                String strategy,
                AnalysisOutcome outcome,
                long durationNanos,
                List<GuardrailCorrection> corrections,
                TokenUsage tokenUsage
        ) {
        }

        @Override
        public void recordFallback(
                String strategy,
                AgentFallbackReason reason,
                long durationNanos,
                TokenUsage tokenUsage
        ) {
        }

        @Override
        public void recordRepair(String strategy, RepairableAgentResponseReason reason, boolean success) {
        }

        @Override
        public void recordValidationFailure(String strategy, RepairableAgentResponseReason reason) {
        }
    }
}

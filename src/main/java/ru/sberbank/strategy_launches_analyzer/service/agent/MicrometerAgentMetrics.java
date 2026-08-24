package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.GuardrailCorrection;
import ru.sberbank.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContract;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

public class MicrometerAgentMetrics implements AgentMetrics {
    private static final String ANALYSIS_METRIC = "strategy.launches.agent.analysis";
    private static final String FALLBACK_METRIC = "strategy.launches.agent.fallback";
    private static final String REPAIR_METRIC = "strategy.launches.agent.repair";
    private static final String VALIDATION_FAILURE_METRIC = "strategy.launches.agent.validation.failure";
    private static final String CORRECTION_METRIC = "strategy.launches.agent.guardrail.correction";
    private static final String DURATION_METRIC = "strategy.launches.agent.duration";
    private static final String LLM_MODEL_INFO_METRIC = "strategy.analysis.llm.model.info";
    private static final String LLM_INPUT_TOKENS_METRIC = "strategy.analysis.llm.input.tokens";
    private static final String LLM_OUTPUT_TOKENS_METRIC = "strategy.analysis.llm.output.tokens";
    private static final String LLM_REQUESTS_METRIC = "strategy.analysis.llm.requests";

    private final MeterRegistry registry;
    private final String model;
    private final String promptHash;

    public MicrometerAgentMetrics(
            MeterRegistry registry,
            String model,
            String promptHash,
            String repairPromptHash,
            Collection<StrategyContract> contracts
    ) {
        this.registry = registry;
        this.model = model;
        this.promptHash = promptHash;
        Gauge.builder(LLM_MODEL_INFO_METRIC, () -> 1.0)
                .tags("model", metricValue(model))
                .register(registry);
        Gauge.builder("strategy.launches.agent.info", () -> 1.0)
                .tags("model", model, "prompt_hash", promptHash, "repair_prompt_hash", repairPromptHash)
                .register(registry);
        contracts.forEach(contract -> Gauge.builder("strategy.launches.contract.info", () -> 1.0)
                .tags("strategy", contract.strategyName(), "contract_version", contract.version())
                .register(registry));
    }

    @Override
    public void recordAnalysis(
            String strategy,
            AnalysisOutcome outcome,
            long durationNanos,
            List<GuardrailCorrection> corrections,
            TokenUsage tokenUsage
    ) {
        Counter.builder(ANALYSIS_METRIC)
                .tags(baseTags(strategy).and("outcome", outcome.metricValue()))
                .register(registry)
                .increment();
        recordLlmUsage(strategy, "completed", tokenUsage);
        recordDuration(strategy, outcome.metricValue(), durationNanos);
        if (corrections != null) {
            corrections.forEach(correction -> Counter.builder(CORRECTION_METRIC)
                    .tags(baseTags(strategy).and("type", correction.type().name().toLowerCase(java.util.Locale.ROOT)))
                    .register(registry)
                    .increment());
        }
    }

    @Override
    public void recordFallback(
            String strategy,
            AgentFallbackReason reason,
            long durationNanos,
            TokenUsage tokenUsage
    ) {
        Counter.builder(ANALYSIS_METRIC)
                .tags(baseTags(strategy).and("outcome", "fallback"))
                .register(registry)
                .increment();
        Counter.builder(FALLBACK_METRIC)
                .tags(baseTags(strategy).and("reason", reason.metricValue()))
                .register(registry)
                .increment();
        recordLlmUsage(strategy, "failed", tokenUsage);
        recordDuration(strategy, "fallback", durationNanos);
    }

    @Override
    public void recordRepair(String strategy, RepairableAgentResponseReason reason, boolean success) {
        Counter.builder(REPAIR_METRIC)
                .tags(baseTags(strategy)
                        .and("reason", reason.metricValue())
                        .and("outcome", success ? "success" : "failure"))
                .register(registry)
                .increment();
    }

    @Override
    public void recordValidationFailure(String strategy, RepairableAgentResponseReason reason) {
        Counter.builder(VALIDATION_FAILURE_METRIC)
                .tags(baseTags(strategy).and("reason", reason.metricValue()))
                .register(registry)
                .increment();
    }

    private void recordDuration(String strategy, String outcome, long durationNanos) {
        Timer.builder(DURATION_METRIC)
                .tags(baseTags(strategy).and("outcome", outcome))
                .register(registry)
                .record(Duration.ofNanos(Math.max(0L, durationNanos)));
    }

    private void recordLlmUsage(String strategy, String outcome, TokenUsage tokenUsage) {
        Tags tags = Tags.of(
                "model", metricValue(model),
                "strategy", metricValue(strategy)
        );
        Counter.builder(LLM_REQUESTS_METRIC)
                .tags(tags.and("outcome", outcome))
                .register(registry)
                .increment();
        increment(LLM_INPUT_TOKENS_METRIC, tags, tokenUsage == null ? null : tokenUsage.inputTokens());
        increment(LLM_OUTPUT_TOKENS_METRIC, tags, tokenUsage == null ? null : tokenUsage.outputTokens());
    }

    private void increment(String name, Tags tags, Number value) {
        Counter.builder(name)
                .tags(tags)
                .register(registry)
                .increment(value == null ? 0.0 : Math.max(0.0, value.doubleValue()));
    }

    private Tags baseTags(String strategy) {
        return Tags.of(
                "strategy", metricValue(strategy),
                "model", metricValue(model),
                "prompt_hash", promptHash
        );
    }

    private static String metricValue(String value) {
        return value == null || value.isBlank() ? "not-provided" : value;
    }
}

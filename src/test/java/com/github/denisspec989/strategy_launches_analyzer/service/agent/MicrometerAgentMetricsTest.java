package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrection;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.GuardrailCorrectionType;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.denisspec989.strategy_launches_analyzer.TestIds.D001;
import static org.assertj.core.api.Assertions.assertThat;

class MicrometerAgentMetricsTest {
    private static final String STRATEGY = "LGD_DIGITAL";
    private static final String MODEL = "configured-model";
    private static final String PROMPT_HASH = "0123456789ab";
    private static final String REPAIR_PROMPT_HASH = "abcdef012345";

    @Test
    void recordsAnalysisRepairValidationCorrectionAndFallbackMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAgentMetrics metrics = metrics(registry);

        metrics.recordValidationFailure(STRATEGY, RepairableAgentResponseReason.INVALID_JSON);
        metrics.recordRepair(STRATEGY, RepairableAgentResponseReason.INVALID_JSON, true);
        metrics.recordAnalysis(
                STRATEGY,
                AgentMetrics.AnalysisOutcome.REPAIRED,
                25_000_000L,
                List.of(new GuardrailCorrection(
                        GuardrailCorrectionType.DIFF_SEVERITY_ESCALATED,
                        D001,
                        "detail that must not become a tag"
                )),
                new TokenUsage(11, 7, 18, 3L, 0L, MODEL)
        );
        metrics.recordFallback(
                STRATEGY,
                AgentFallbackReason.CAPACITY,
                10_000_000L,
                TokenUsage.zero()
        );

        assertThat(counter(registry, "strategy.launches.agent.validation.failure",
                "reason", "invalid_json")).isEqualTo(1.0);
        assertThat(counter(registry, "strategy.launches.agent.repair",
                "reason", "invalid_json", "outcome", "success")).isEqualTo(1.0);
        assertThat(counter(registry, "strategy.launches.agent.analysis",
                "outcome", "repaired")).isEqualTo(1.0);
        assertThat(counter(registry, "strategy.launches.agent.analysis",
                "outcome", "fallback")).isEqualTo(1.0);
        assertThat(counter(registry, "strategy.launches.agent.fallback",
                "reason", "capacity")).isEqualTo(1.0);
        assertThat(counter(registry, "strategy.launches.agent.guardrail.correction",
                "type", "diff_severity_escalated")).isEqualTo(1.0);
        assertThat(registry.get("strategy.launches.agent.duration")
                .tag("strategy", STRATEGY)
                .tag("outcome", "repaired")
                .timer()
                .count()).isEqualTo(1);
        assertThat(llmCounter(registry, "strategy.analysis.llm.input.tokens")).isEqualTo(11.0);
        assertThat(llmCounter(registry, "strategy.analysis.llm.output.tokens")).isEqualTo(7.0);
        assertThat(llmCounter(registry, "strategy.analysis.llm.requests", "outcome", "completed"))
                .isEqualTo(1.0);
        assertThat(llmCounter(registry, "strategy.analysis.llm.requests", "outcome", "failed"))
                .isEqualTo(1.0);
    }

    @Test
    void recordsTransportAndInternalFailuresWithConsumedTokens() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAgentMetrics metrics = metrics(registry);

        metrics.recordFallback(
                STRATEGY,
                AgentFallbackReason.TRANSPORT,
                1_000_000L,
                new TokenUsage(4, 3, 7, 0L, 0L, "provider-reported-model")
        );
        metrics.recordFallback(
                STRATEGY,
                AgentFallbackReason.INTERNAL,
                2_000_000L,
                new TokenUsage(2, 1, 3, 0L, 0L, "another-provider-model")
        );

        assertThat(llmCounter(registry, "strategy.analysis.llm.requests", "outcome", "failed"))
                .isEqualTo(2.0);
        assertThat(llmCounter(registry, "strategy.analysis.llm.input.tokens")).isEqualTo(6.0);
        assertThat(llmCounter(registry, "strategy.analysis.llm.output.tokens")).isEqualTo(4.0);
        assertThat(registry.get("strategy.analysis.llm.requests")
                .tag("model", MODEL)
                .tag("strategy", STRATEGY)
                .tag("outcome", "failed")
                .counter()
                .getId()
                .getTags())
                .extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("model", "strategy", "outcome");
    }

    @Test
    void exposesVersionInfoWithoutHighCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metrics(registry);

        assertThat(registry.get("strategy.launches.agent.info")
                .tag("model", MODEL)
                .tag("prompt_hash", PROMPT_HASH)
                .tag("repair_prompt_hash", REPAIR_PROMPT_HASH)
                .gauge()
                .value()).isEqualTo(1.0);
        assertThat(registry.get("strategy.analysis.llm.model.info")
                .tag("model", MODEL)
                .gauge()
                .value()).isEqualTo(1.0);
        assertThat(registry.get("strategy.launches.contract.info")
                .tag("strategy", STRATEGY)
                .gauge()
                .value()).isEqualTo(1.0);
        assertThat(registry.getMeters())
                .flatMap(meter -> meter.getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContain("request_id", "path", "diff_id", "exception", "message");
    }

    private static MicrometerAgentMetrics metrics(SimpleMeterRegistry registry) {
        StrategyContractRegistry contracts = new StrategyContractRegistry(new OpenApiStrategyContractLoader());
        return new MicrometerAgentMetrics(
                registry,
                MODEL,
                PROMPT_HASH,
                REPAIR_PROMPT_HASH,
                contracts.contracts()
        );
    }

    private static double counter(SimpleMeterRegistry registry, String name, String... tags) {
        var search = registry.get(name).tag("strategy", STRATEGY).tag("model", MODEL)
                .tag("prompt_hash", PROMPT_HASH);
        for (int index = 0; index < tags.length; index += 2) {
            search = search.tag(tags[index], tags[index + 1]);
        }
        return search.counter().count();
    }

    private static double llmCounter(SimpleMeterRegistry registry, String name, String... tags) {
        var search = registry.get(name).tag("strategy", STRATEGY).tag("model", MODEL);
        for (int index = 0; index < tags.length; index += 2) {
            search = search.tag(tags[index], tags[index + 1]);
        }
        return search.counter().count();
    }
}

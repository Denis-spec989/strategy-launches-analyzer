package com.github.denisspec989.strategy_launches_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.BadRequestException;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentExecutionFailureException;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompareStrategyLaunchesUseCaseTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StrategyContractRegistry registry = new StrategyContractRegistry(new OpenApiStrategyContractLoader());
    private final StrategyDiffEngine diffEngine = new StrategyDiffEngine(new ContractValidator());

    @Test
    void returnsDeterministicFallbackForCallsBeyondBulkheadLimit() throws Exception {
        CountDownLatch insideAgent = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AgentAnalyzer blockingAgent = input -> {
            insideAgent.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return completedAnalysis();
        };
        CompareStrategyLaunchesUseCase useCase =
                new CompareStrategyLaunchesUseCase(diffEngine, registry, blockingAgent, 1, 100_000, 100);
        CompareStrategyRequest request = request("model-change");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> occupying = executor.submit(() -> useCase.compare(request));
            assertThat(insideAgent.await(5, TimeUnit.SECONDS)).isTrue();

            CompareStrategyResponse fallback = useCase.compare(request);

            assertThat(fallback.agentAnalysis().status()).isEqualTo(AgentAnalysisStatus.FAILED);
            assertThat(fallback.agentAnalysis().overallSeverity())
                    .isEqualTo(fallback.summary().deterministicSeverity());
            assertThat(fallback.diffs()).isNotEmpty();
            assertThat(fallback.agentAnalysis().errorMessage()).isEqualTo("LLM-анализ недоступен.");

            release.countDown();
            occupying.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void responseCarriesContractVersionAndAnalyzedAt() {
        AgentAnalyzer stubAgent = input -> completedAnalysis();
        CompareStrategyLaunchesUseCase useCase =
                new CompareStrategyLaunchesUseCase(diffEngine, registry, stubAgent, 20, 100_000, 100);
        CompareStrategyRequest request = request("model-change");

        CompareStrategyResponse response = useCase.compare(request);

        assertThat(response.contractVersion())
                .isEqualTo(registry.get(StrategyName.LGD_DIGITAL).version())
                .isNotBlank();
        assertThat(response.analyzedAt()).isNotNull();
    }

    @Test
    void rejectsLaunchPayloadExceedingNodeLimit() {
        AgentAnalyzer stubAgent = input -> completedAnalysis();
        CompareStrategyLaunchesUseCase useCase =
                new CompareStrategyLaunchesUseCase(diffEngine, registry, stubAgent, 20, 5, 100);
        CompareStrategyRequest request = request("model-change");

        assertThatThrownBy(() -> useCase.compare(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("node limit");
    }

    @Test
    void publicResponseAndAgentInputExposeTheSameDeterministicSeverity() {
        AtomicReference<AgentAnalysisInput> capturedInput = new AtomicReference<>();
        CompareStrategyLaunchesUseCase useCase = new CompareStrategyLaunchesUseCase(
                diffEngine,
                registry,
                input -> {
                    capturedInput.set(input);
                    return completedAnalysis();
                },
                20,
                100_000,
                100
        );

        CompareStrategyResponse response = useCase.compare(request("model-change"));
        JsonNode responseJson = objectMapper.valueToTree(response);

        assertThat(responseJson.path("diffs").isArray()).isTrue();
        assertThat(responseJson.path("diffs"))
                .allSatisfy(diff -> assertThat(diff.path("deterministicSeverity").asText()).isNotBlank());
        assertThat(response.diffs()).allSatisfy(diff ->
                assertThat(diff.deterministicSeverity()).isIn(Severity.WARNING, Severity.CRITICAL));
        assertThat(capturedInput.get().diffs()).containsExactlyElementsOf(response.diffs());
    }

    @Test
    void returnsDeterministicFallbackWhenAgentThrows() {
        CompareStrategyLaunchesUseCase useCase = new CompareStrategyLaunchesUseCase(
                diffEngine,
                registry,
                input -> {
                    throw new IllegalStateException("provider unavailable");
                },
                20,
                100_000,
                100
        );

        CompareStrategyResponse response = useCase.compare(request("model-change"));

        assertThat(response.agentAnalysis().status()).isEqualTo(AgentAnalysisStatus.FAILED);
        assertThat(response.agentAnalysis().overallSeverity()).isEqualTo(response.summary().deterministicSeverity());
        assertThat(response.agentAnalysis().summary()).contains("Детерминированные отличия");
        assertThat(response.agentAnalysis().businessImpact()).isNotBlank();
        assertThat(response.diffs()).isNotEmpty();
    }

    @Test
    void fallbackPreservesTokensConsumedBeforeRepairExhaustion() {
        TokenUsage consumed = new TokenUsage(11, 7, 18, 3L, 0L, "actual-model");
        CompareStrategyLaunchesUseCase useCase = new CompareStrategyLaunchesUseCase(
                diffEngine,
                registry,
                input -> {
                    throw new AgentExecutionFailureException(
                            "repair exhausted",
                            null,
                            AgentFallbackReason.REPAIR_EXHAUSTED,
                            consumed
                    );
                },
                20,
                100_000,
                100
        );

        CompareStrategyResponse response = useCase.compare(request("model-change"));

        assertThat(response.agentAnalysis().status()).isEqualTo(AgentAnalysisStatus.FAILED);
        assertThat(response.agentAnalysis().tokenUsage()).isEqualTo(consumed);
    }

    private CompareStrategyRequest request(String fixture) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/main.json".formatted(fixture));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/shadow.json".formatted(fixture));
        return new CompareStrategyRequest(StrategyName.LGD_DIGITAL, main, shadow, null);
    }

    private static AgentAnalysis completedAnalysis() {
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                Severity.WARNING,
                "summary",
                "business impact",
                "technical risks",
                List.of(),
                List.of(),
                TokenUsage.zero(),
                null
        );
    }
}

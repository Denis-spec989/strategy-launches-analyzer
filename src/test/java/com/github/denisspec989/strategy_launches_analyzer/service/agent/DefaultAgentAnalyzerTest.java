package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.node.TextNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentExecutionFailureException;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.RepairableAgentResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAgentAnalyzerTest {
    @Test
    void productionAnalyzerPassesConfiguredModelToClient() {
        AtomicReference<AgentCallOptions> captured = new AtomicReference<>();
        AgentModelClient client = (input, options) -> {
            captured.set(options);
            return new AgentModelCallResult(
                    new StructuredAgentAnalysis(
                            Severity.INFO,
                            "Отличий нет.",
                            "Бизнес-влияние отсутствует.",
                            "Технические риски отсутствуют.",
                            List.of("Продолжить штатный контроль."),
                            List.of()
                    ),
                    TokenUsage.zero(),
                    options.model(),
                    options.model(),
                    1
            );
        };
        AgentAnalysisInput input = new AgentAnalysisInput(
                "LGD_DIGITAL",
                new ComparisonSummary("LGD_DIGITAL", 0, 0, 0, 0, 0, 0, false, Severity.INFO),
                List.of(),
                List.of(),
                List.of(),
                null
        );
        DefaultAgentAnalyzer analyzer = new DefaultAgentAnalyzer(
                client,
                new DefaultAgentAnalysisPostProcessor(),
                "configured-model"
        );

        analyzer.analyze(input);

        assertThat(captured.get().model()).isEqualTo("configured-model");
        assertThat(captured.get().repairContext()).isNull();
    }

    @ParameterizedTest
    @EnumSource(RepairableAgentResponseReason.class)
    void retriesEveryRepairableResponseReasonExactlyOnce(RepairableAgentResponseReason reason) {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient client = (input, options) -> {
            if (calls.incrementAndGet() == 1) {
                throw repairable(reason, usage(2, 1));
            }
            assertThat(options.repairContext()).isNotNull();
            assertThat(options.repairContext().reason()).isEqualTo(reason);
            return result(options, validResponse(), usage(3, 2));
        };
        DefaultAgentAnalyzer analyzer = analyzer(client);

        AgentAnalysis analysis = analyzer.analyze(emptyInput());

        assertThat(calls).hasValue(2);
        assertThat(analysis.status()).isEqualTo(AgentAnalysisStatus.COMPLETED);
        assertThat(analysis.tokenUsage().inputTokens()).isEqualTo(5);
        assertThat(analysis.tokenUsage().outputTokens()).isEqualTo(3);
    }

    @Test
    void repairCarriesParsedResponseAndValidationViolations() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<AgentCallOptions> repairOptions = new AtomicReference<>();
        StructuredAgentAnalysis invalid = new StructuredAgentAnalysis(
                Severity.INFO,
                " ",
                "Влияние отсутствует.",
                "Риски отсутствуют.",
                List.of(),
                List.of()
        );
        AgentModelClient client = (input, options) -> {
            if (calls.incrementAndGet() == 1) {
                return result(options, invalid, usage(5, 2));
            }
            repairOptions.set(options);
            return result(options, validResponse(), usage(7, 3));
        };

        AgentAnalysis analysis = analyzer(client).analyze(emptyInput());

        assertThat(repairOptions.get().repairContext().previousResponse()).isSameAs(invalid);
        assertThat(repairOptions.get().repairContext().violations()).contains("summary is blank");
        assertThat(analysis.tokenUsage().inputTokens()).isEqualTo(12);
        assertThat(analysis.tokenUsage().outputTokens()).isEqualTo(5);
    }

    @Test
    void stopsAfterFailedRepairAndPreservesConsumedTokens() {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient client = (input, options) -> {
            int call = calls.incrementAndGet();
            throw repairable(
                    call == 1
                            ? RepairableAgentResponseReason.INVALID_JSON
                            : RepairableAgentResponseReason.CONTRACT_VIOLATION,
                    usage(call, call)
            );
        };

        assertThatThrownBy(() -> analyzer(client).analyze(emptyInput()))
                .isInstanceOfSatisfying(AgentExecutionFailureException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(AgentFallbackReason.REPAIR_EXHAUSTED);
                    assertThat(ex.tokenUsage().inputTokens()).isEqualTo(3);
                    assertThat(ex.tokenUsage().outputTokens()).isEqualTo(3);
                });
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotCountFirstCallTokensTwiceWhenRepairedResponseViolatesContract() {
        AtomicInteger calls = new AtomicInteger();
        StructuredAgentAnalysis invalid = new StructuredAgentAnalysis(
                Severity.INFO,
                " ",
                "Влияние отсутствует.",
                "Риски отсутствуют.",
                List.of(),
                List.of()
        );
        AgentModelClient client = (input, options) -> {
            int call = calls.incrementAndGet();
            return result(options, invalid, call == 1 ? usage(2, 1) : usage(3, 2));
        };

        assertThatThrownBy(() -> analyzer(client).analyze(emptyInput()))
                .isInstanceOfSatisfying(AgentExecutionFailureException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(AgentFallbackReason.REPAIR_EXHAUSTED);
                    assertThat(ex.tokenUsage().inputTokens()).isEqualTo(5);
                    assertThat(ex.tokenUsage().outputTokens()).isEqualTo(3);
                });
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRepairTransportFailure() {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient client = (input, options) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("connection reset");
        };

        assertThatThrownBy(() -> analyzer(client).analyze(emptyInput()))
                .isInstanceOfSatisfying(AgentExecutionFailureException.class, ex ->
                        assertThat(ex.reason()).isEqualTo(AgentFallbackReason.TRANSPORT));
        assertThat(calls).hasValue(1);
    }

    @Test
    void preservesFirstResponseTokensWhenPostProcessingFailsUnexpectedly() {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient client = (input, options) -> {
            calls.incrementAndGet();
            return result(options, validResponse(), usage(4, 2));
        };
        AgentAnalysisPostProcessor failingPostProcessor = (raw, input, tokenUsage) -> {
            throw new IllegalStateException("post-processing failed");
        };
        DefaultAgentAnalyzer analyzer = new DefaultAgentAnalyzer(
                client,
                failingPostProcessor,
                "configured-model"
        );

        assertThatThrownBy(() -> analyzer.analyze(emptyInput()))
                .isInstanceOfSatisfying(AgentExecutionFailureException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(AgentFallbackReason.INTERNAL);
                    assertThat(ex.tokenUsage().inputTokens()).isEqualTo(4);
                    assertThat(ex.tokenUsage().outputTokens()).isEqualTo(2);
                });
        assertThat(calls).hasValue(1);
    }

    @Test
    void canDisableRepairWithoutChangingFallbackContract() {
        AtomicInteger calls = new AtomicInteger();
        AgentModelClient client = (input, options) -> {
            calls.incrementAndGet();
            throw repairable(RepairableAgentResponseReason.INVALID_JSON, usage(2, 1));
        };
        DefaultAgentAnalyzer analyzer = new DefaultAgentAnalyzer(
                client,
                new DefaultAgentAnalysisPostProcessor(),
                "configured-model",
                false,
                AgentMetrics.noop()
        );

        assertThatThrownBy(() -> analyzer.analyze(emptyInput()))
                .isInstanceOfSatisfying(AgentExecutionFailureException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(AgentFallbackReason.RESPONSE_INVALID);
                    assertThat(ex.tokenUsage().totalTokens()).isEqualTo(3);
                });
        assertThat(calls).hasValue(1);
    }

    @Test
    void localGuardrailCorrectionDoesNotTriggerRepair() {
        AtomicInteger calls = new AtomicInteger();
        DiffEntry diff = new DiffEntry(
                "D001",
                "strategyResponse.lgdData.lgdModel",
                DiffType.STRING_VALUE_CHANGED,
                DiffCategory.MODEL,
                TextNode.valueOf("main"),
                TextNode.valueOf("shadow"),
                null,
                null,
                Severity.WARNING,
                "Model changed."
        );
        AgentAnalysisInput input = new AgentAnalysisInput(
                "LGD_DIGITAL",
                new ComparisonSummary("LGD_DIGITAL", 1, 0, 1, 0, 0, 0, false, Severity.WARNING),
                List.of(diff),
                List.of(),
                List.of(),
                null
        );
        AgentModelClient client = (analysisInput, options) -> {
            calls.incrementAndGet();
            return result(options, new StructuredAgentAnalysis(
                    Severity.WARNING,
                    "Изменилась модель.",
                    "Требуется проверка.",
                    "Контрактных рисков нет.",
                    List.of("Проверить изменение."),
                    List.of()
            ), TokenUsage.zero());
        };

        AgentAnalysis analysis = analyzer(client).analyze(input);

        assertThat(calls).hasValue(1);
        assertThat(analysis.diffExplanations()).singleElement().satisfies(explanation ->
                assertThat(explanation.diffId()).isEqualTo("D001"));
    }

    private static DefaultAgentAnalyzer analyzer(AgentModelClient client) {
        return new DefaultAgentAnalyzer(
                client,
                new DefaultAgentAnalysisPostProcessor(),
                "configured-model"
        );
    }

    private static AgentAnalysisInput emptyInput() {
        return new AgentAnalysisInput(
                "LGD_DIGITAL",
                new ComparisonSummary("LGD_DIGITAL", 0, 0, 0, 0, 0, 0, false, Severity.INFO),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    private static StructuredAgentAnalysis validResponse() {
        return new StructuredAgentAnalysis(
                Severity.INFO,
                "Отличий нет.",
                "Бизнес-влияние отсутствует.",
                "Технические риски отсутствуют.",
                List.of("Продолжить штатный контроль."),
                List.of()
        );
    }

    private static AgentModelCallResult result(
            AgentCallOptions options,
            StructuredAgentAnalysis response,
            TokenUsage usage
    ) {
        return new AgentModelCallResult(response, usage, options.model(), options.model(), 1);
    }

    private static RepairableAgentResponseException repairable(
            RepairableAgentResponseReason reason,
            TokenUsage usage
    ) {
        return new RepairableAgentResponseException(
                "invalid response",
                reason,
                List.of("response is invalid"),
                null,
                usage
        );
    }

    private static TokenUsage usage(int input, int output) {
        return new TokenUsage(input, output, input + output, 0L, 0L, "configured-model");
    }
}

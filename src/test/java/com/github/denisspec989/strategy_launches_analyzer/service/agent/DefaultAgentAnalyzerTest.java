package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
    }
}

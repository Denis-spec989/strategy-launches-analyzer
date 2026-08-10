package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.ai.openai.api-key=${OPENAI_API_KEY:benchmark-key-missing}",
                "spring.ai.openai.chat.api-key=${OPENAI_API_KEY:benchmark-key-missing}"
        }
)
@ActiveProfiles("openai")
class AgentBenchmarkIT {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AgentModelClient modelClient;
    @Autowired
    private AgentAnalysisPostProcessor postProcessor;
    @Autowired
    private StrategyDiffEngine diffEngine;
    @Autowired
    private StrategyContractRegistry contractRegistry;
    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void comparesConfiguredModelsAndSelectsWinner() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is required for the benchmark profile. "
                            + "Regular mvn test does not require it."
            );
        }
        BenchmarkConfiguration configuration = BenchmarkConfiguration.fromSystemProperties();
        SemanticJudge judge = new OpenAiSemanticJudge(
                chatClientBuilder.build(), objectMapper, configuration.judgeModel()
        );
        BenchmarkRunner runner = new BenchmarkRunner(
                objectMapper,
                modelClient,
                postProcessor,
                judge,
                new BenchmarkInputFactory(diffEngine, contractRegistry)
        );

        BenchmarkSummary summary = runner.run(configuration);

        assertThat(summary.winner()).isNotBlank();
    }
}

package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
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
    private GigaChatStructuredCompletionClient completionClient;

    @Test
    void comparesConfiguredModelsAndSelectsWinner() {
        BenchmarkConfiguration configuration = BenchmarkConfiguration.fromSystemProperties();
        JudgeCalibrationGate.requireAccepted(
                objectMapper,
                Path.of(System.getProperty(
                        "benchmark.judge-calibration-report",
                        "target/judge-calibration/v2/report.json"
                )),
                Path.of(System.getProperty(
                        "benchmark.judge-calibration-dataset",
                        "src/test/resources/evals/judge-calibration/v2/calibration-cases.jsonl"
                )),
                configuration.judgeModel()
        );
        SemanticJudge judge = new GigaChatSemanticJudge(
                completionClient, objectMapper, configuration.judgeModel()
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

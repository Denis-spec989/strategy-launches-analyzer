package com.github.denisspec989.strategy_launches_analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalyzer;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CompareStrategyLaunchesUseCaseLoggingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void infoLogsDoNotContainPayloadPromptOrFullResponse(CapturedOutput output) {
        CompareStrategyLaunchesUseCase useCase = new CompareStrategyLaunchesUseCase(
                new StrategyDiffEngine(new ContractValidator()),
                new StrategyContractRegistry(new OpenApiStrategyContractLoader()),
                successfulAgent()
        );

        useCase.compare(requestBody());

        assertThat(output.getOut())
                .contains("comparison request accepted")
                .contains("deterministic comparison completed")
                .contains("agent analysis completed")
                .doesNotContain("Корпоративные клиенты 2022")
                .doesNotContain("Корпоративные клиенты 2023")
                .doesNotContain("\"mainLaunch\"")
                .doesNotContain("\"shadowLaunch\"")
                .doesNotContain("\"diffs\"")
                .doesNotContain("agent analysis input")
                .doesNotContain("response={");
    }

    private CompareStrategyRequest requestBody() {
        ObjectNode metadata = objectMapper.createObjectNode()
                .put("requestId", "REQ-LOG")
                .put("mainLaunchId", "MAIN-1")
                .put("shadowLaunchId", "SHADOW-1")
                .put("mainStrategyVersion", "2022")
                .put("shadowStrategyVersion", "2023");
        return new CompareStrategyRequest(
                StrategyName.LGD_DIGITAL,
                TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/main.json"),
                TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/shadow.json"),
                objectMapper.convertValue(metadata, com.github.denisspec989.strategy_launches_analyzer.dto.api.LaunchMetadata.class)
        );
    }

    private AgentAnalyzer successfulAgent() {
        return input -> new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                Severity.WARNING,
                "summary",
                "business",
                "risks",
                List.of("recommendation"),
                List.of(),
                TokenUsage.zero(),
                null
        );
    }
}

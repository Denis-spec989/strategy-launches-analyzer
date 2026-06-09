package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.LgdDigitalContract;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.LgdDigitalDiffEngine;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAnalyzerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private LgdDigitalContract contract;
    private LgdDigitalDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        contract = new LgdDigitalContract();
        diffEngine = new LgdDigitalDiffEngine(contract, new ContractValidator(contract));
    }

    @Test
    void promptContainsOnlyNormalizedComparisonPayloadAndTouchedContractContext() {
        AgentAnalysisInput input = inputForFixture("model-change");

        String prompt = new AgentPromptBuilder(objectMapper).buildUserPrompt(input);

        assertThat(prompt).contains("\"diffs\"");
        assertThat(prompt).contains("\"contractValidation\"");
        assertThat(prompt).contains("\"contractContext\"");
        assertThat(prompt).contains("\"deterministicSeverity\"");
        assertThat(prompt).doesNotContain("\"highestSeverity\"");
        assertThat(AgentPromptBuilder.SYSTEM_PROMPT).contains("not as final business severity");
        assertThat(prompt).contains("\"format\" : \"double\"");
        assertThat(prompt).contains("LGD-\u043F\u043E\u0442\u0435\u0440\u0438 \u043F\u0440\u0438 \u0434\u0435\u0444\u043E\u043B\u0442\u0435 (%)");
        assertThat(prompt).contains("\u041C\u043E\u0434\u0435\u043B\u044C \u0440\u0430\u0441\u0447\u0435\u0442\u0430");
        assertThat(prompt).contains("LGD \u043F\u0440\u0438 \u044D\u043A\u043E\u043D\u043E\u043C\u0438\u0447\u0435\u0441\u043A\u043E\u043C \u0441\u043F\u0430\u0434\u0435 (%)");
        assertThat(prompt).doesNotContain("\u0420\u0435\u0436\u0438\u043C \u0440\u0430\u0441\u0447\u0435\u0442\u0430");
        assertThat(prompt).doesNotContain("\"mainLaunch\"");
        assertThat(prompt).doesNotContain("\"shadowLaunch\"");
    }

    @Test
    void fallbackAgentExplainsModelAndMetricDiffWithoutClaimingRootCause() {
        AgentAnalysis analysis = new FallbackAgentAnalyzer().analyze(inputForFixture("model-change"));

        assertThat(analysis.status()).isEqualTo(AgentAnalysisStatus.COMPLETED);
        assertThat(analysis.overallSeverity()).isEqualTo(Severity.WARNING);
        assertThat(analysis.businessImpact()).contains("may explain");
        assertThat(analysis.businessImpact()).contains("should be confirmed");
        assertThat(analysis.diffExplanations()).hasSize(3);
        assertThat(analysis.tokenUsage().totalTokens()).isZero();
    }

    @Test
    void fallbackAgentMarksModeRegressionAsCritical() {
        AgentAnalysis analysis = new FallbackAgentAnalyzer().analyze(inputForFixture("mode-regression"));

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(analysis.technicalRisks()).contains("constants");
        assertThat(analysis.recommendations()).anySatisfy(recommendation ->
                assertThat(recommendation).contains("response constants"));
    }

    @Test
    void fallbackAgentMarksEmptyComparisonAsInfo() {
        AgentAnalysis analysis = new FallbackAgentAnalyzer().analyze(inputForFixture("model-change", "main", "main"));

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.INFO);
        assertThat(analysis.recommendations()).containsExactly("No action is required for this launch pair.");
    }

    @Test
    void springAiMappingKeepsHardTechnicalDiffCriticalWhenModelDowngradesIt() {
        AgentAnalysis analysis = SpringAiAgentAnalyzer.toDomain(
                warningStructuredResponse(),
                inputForFixture("mode-regression"),
                TokenUsage.zero()
        );

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void springAiMappingKeepsCriticalContractIssueCriticalWhenModelDowngradesIt() {
        ContractIssue issue = new ContractIssue(
                "C001",
                LaunchSide.SHADOW,
                "strategyResponse.lgdData.lgd",
                ContractIssueType.REQUIRED_FIELD_MISSING,
                Severity.CRITICAL,
                "number, 1..1",
                "missing",
                null,
                "Required field is missing."
        );
        AgentAnalysisInput input = new AgentAnalysisInput(
                LgdDigitalContract.STRATEGY_NAME,
                ComparisonSummary.from(LgdDigitalContract.STRATEGY_NAME, List.of(), List.of(issue)),
                List.of(),
                List.of(issue),
                List.of(),
                null
        );

        AgentAnalysis analysis = SpringAiAgentAnalyzer.toDomain(warningStructuredResponse(), input, TokenUsage.zero());

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
    }

    private AgentAnalysisInput inputForFixture(String fixtureName) {
        return inputForFixture(fixtureName, "main", "shadow");
    }

    private AgentAnalysisInput inputForFixture(String fixtureName, String mainLaunchName, String shadowLaunchName) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/%s.json".formatted(fixtureName, mainLaunchName));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/%s.json".formatted(fixtureName, shadowLaunchName));
        DiffResult diffResult = diffEngine.compare(main, shadow);
        ComparisonSummary summary = ComparisonSummary.from(
                LgdDigitalContract.STRATEGY_NAME,
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        return new AgentAnalysisInput(
                LgdDigitalContract.STRATEGY_NAME,
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                contractContext(diffResult),
                null
        );
    }

    private StructuredAgentAnalysis warningStructuredResponse() {
        return new StructuredAgentAnalysis(
                Severity.WARNING,
                "summary",
                "business impact",
                "technical risks",
                List.of("recommendation"),
                List.of()
        );
    }

    private List<ContractFieldContext> contractContext(DiffResult diffResult) {
        List<String> touchedPaths = java.util.stream.Stream.concat(
                        diffResult.diffs().stream().map(diff -> diff.path()),
                        diffResult.contractValidation().stream().map(issue -> issue.path())
                )
                .distinct()
                .toList();
        return contract.fields().stream()
                .filter(field -> touchedPaths.contains(field.path()))
                .map(ContractFieldContext::from)
                .toList();
    }
}

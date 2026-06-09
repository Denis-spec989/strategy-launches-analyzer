package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContract;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAnalyzerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StrategyContract contract;
    private StrategyDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        contract = new StrategyContractRegistry(new OpenApiStrategyContractLoader()).get(StrategyName.LGD_DIGITAL);
        diffEngine = new StrategyDiffEngine(new ContractValidator());
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
        assertThat(AgentPromptBuilder.SYSTEM_PROMPT).doesNotContain("LGD_DIGITAL");
        assertThat(AgentPromptBuilder.SYSTEM_PROMPT).contains("not as final business severity");
        assertThat(prompt).contains("\"strategyName\" : \"LGD_DIGITAL\"");
        assertThat(prompt).contains("\"format\" : \"double\"");
        assertThat(prompt).contains("LGD-\u043F\u043E\u0442\u0435\u0440\u0438 \u043F\u0440\u0438 \u0434\u0435\u0444\u043E\u043B\u0442\u0435 (%)");
        assertThat(prompt).contains("\u041C\u043E\u0434\u0435\u043B\u044C \u0440\u0430\u0441\u0447\u0435\u0442\u0430");
        assertThat(prompt).contains("LGD \u043F\u0440\u0438 \u044D\u043A\u043E\u043D\u043E\u043C\u0438\u0447\u0435\u0441\u043A\u043E\u043C \u0441\u043F\u0430\u0434\u0435 (%)");
        assertThat(prompt).doesNotContain("\u0420\u0435\u0436\u0438\u043C \u0440\u0430\u0441\u0447\u0435\u0442\u0430");
        assertThat(prompt).doesNotContain("\"mainLaunch\"");
        assertThat(prompt).doesNotContain("\"shadowLaunch\"");
    }

    @Test
    void springAiMappingKeepsHardTechnicalDiffCriticalWhenModelDowngradesIt() {
        AgentAnalysisInput input = inputForFixture("mode-regression");
        AgentAnalysis analysis = SpringAiAgentAnalyzer.toDomain(
                warningStructuredResponse(List.of(explanationFor(input.diffs().get(0)))),
                input,
                TokenUsage.zero()
        );

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(analysis.status()).isEqualTo(AgentAnalysisStatus.COMPLETED);
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
                contract.strategyName(),
                ComparisonSummary.from(contract.strategyName(), List.of(), List.of(issue)),
                List.of(),
                List.of(issue),
                List.of(),
                null
        );

        AgentAnalysis analysis = SpringAiAgentAnalyzer.toDomain(warningStructuredResponse(List.of()), input, TokenUsage.zero());

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void springAiMappingRejectsFabricatedDiffId() {
        AgentAnalysisInput input = inputForFixture("model-change");

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(
                warningStructuredResponse(List.of(new DiffExplanation(
                        "D999",
                        "strategyResponse.lgdData.lgd",
                        Severity.WARNING,
                        "explanation"
                ))),
                input,
                TokenUsage.zero()
        )).isInstanceOf(AgentAnalysisException.class);
    }

    @Test
    void springAiMappingRejectsMismatchedDiffPath() {
        AgentAnalysisInput input = inputForFixture("model-change");

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(
                warningStructuredResponse(List.of(new DiffExplanation(
                        "D001",
                        "strategyResponse.lgdData.lgdModel",
                        Severity.WARNING,
                        "explanation"
                ))),
                input,
                TokenUsage.zero()
        )).isInstanceOf(AgentAnalysisException.class);
    }

    @Test
    void springAiMappingRejectsNullDiffSeverity() {
        AgentAnalysisInput input = inputForFixture("model-change");

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(
                warningStructuredResponse(List.of(new DiffExplanation(
                        "D001",
                        "strategyResponse.lgdData.lgd",
                        null,
                        "explanation"
                ))),
                input,
                TokenUsage.zero()
        )).isInstanceOf(AgentAnalysisException.class);
    }

    @Test
    void springAiMappingRejectsBlankDiffExplanation() {
        AgentAnalysisInput input = inputForFixture("model-change");

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(
                warningStructuredResponse(List.of(new DiffExplanation(
                        "D001",
                        "strategyResponse.lgdData.lgd",
                        Severity.WARNING,
                        " "
                ))),
                input,
                TokenUsage.zero()
        )).isInstanceOf(AgentAnalysisException.class);
    }

    @Test
    void springAiMappingRejectsMissingHardCriticalDiffExplanation() {
        AgentAnalysisInput input = inputForFixture("mode-regression");

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(
                warningStructuredResponse(List.of()),
                input,
                TokenUsage.zero()
        )).isInstanceOf(AgentAnalysisException.class);
    }

    @Test
    void springAiMappingRejectsOversizedRecommendationList() {
        AgentAnalysisInput input = inputForFixture("model-change");

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(
                structuredResponse(Severity.WARNING, List.of(
                        "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "r10", "r11"
                ), List.of()),
                input,
                TokenUsage.zero()
        )).isInstanceOf(AgentAnalysisException.class);
    }

    private AgentAnalysisInput inputForFixture(String fixtureName) {
        return inputForFixture(fixtureName, "main", "shadow");
    }

    private AgentAnalysisInput inputForFixture(String fixtureName, String mainLaunchName, String shadowLaunchName) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/%s.json".formatted(fixtureName, mainLaunchName));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/%s.json".formatted(fixtureName, shadowLaunchName));
        DiffResult diffResult = diffEngine.compare(contract, main, shadow);
        ComparisonSummary summary = ComparisonSummary.from(
                contract.strategyName(),
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        return new AgentAnalysisInput(
                contract.strategyName(),
                summary,
                diffResult.diffs(),
                diffResult.contractValidation(),
                contractContext(diffResult),
                null
        );
    }

    private StructuredAgentAnalysis warningStructuredResponse(List<DiffExplanation> diffExplanations) {
        return structuredResponse(Severity.WARNING, List.of("recommendation"), diffExplanations);
    }

    private StructuredAgentAnalysis structuredResponse(
            Severity severity,
            List<String> recommendations,
            List<DiffExplanation> diffExplanations
    ) {
        return new StructuredAgentAnalysis(
                severity,
                "summary",
                "business impact",
                "technical risks",
                recommendations,
                diffExplanations
        );
    }

    private DiffExplanation explanationFor(com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry diff) {
        return new DiffExplanation(diff.id(), diff.path(), Severity.WARNING, "explanation");
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

package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DeterministicSeverityCalculator;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContract;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
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
        assertThat(prompt).contains("summary, businessImpact, technicalRisks, recommendations");
        assertThat(prompt).contains("only from summary, diffs, contractValidation, contractContext, and metadata");
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
    void systemPromptDefinesAnalyticalOutputFieldContract() {
        String systemPrompt = AgentPromptBuilder.SYSTEM_PROMPT;

        assertThat(systemPrompt).contains("Output field contract:");
        assertThat(systemPrompt).contains("summary: Briefly summarize the overall deterministic diffs");
        assertThat(systemPrompt).contains("Do not include business impact conclusions");
        assertThat(systemPrompt).contains("businessImpact: Explain how the diffs can affect business interpretation");
        assertThat(systemPrompt).contains("contractContext.description");
        assertThat(systemPrompt).contains("summaryGuidance");
        assertThat(systemPrompt).contains("shadow-minus-main direction");
        assertThat(systemPrompt).contains("technicalRisks: Explain technical and contract risks");
        assertThat(systemPrompt).contains("contract/schema/type/nullability issues");
        assertThat(systemPrompt).contains("unknown fields");
        assertThat(systemPrompt).contains("serialization/mapping/integration mode regressions");
        assertThat(systemPrompt).contains("recommendations: Return concrete actionable follow-up actions");
        assertThat(systemPrompt).contains("blocking promotion for CRITICAL issues");
    }

    @Test
    void springAiMappingKeepsHardTechnicalDiffCriticalWhenModelDowngradesIt() {
        AgentAnalysisInput input = inputForFixture("mode-regression");

        AgentAnalysis analysis = SpringAiAgentAnalyzer.toDomain(
                structuredResponse(input, Severity.WARNING),
                input,
                TokenUsage.zero()
        );

        assertThat(analysis.status()).isEqualTo(AgentAnalysisStatus.COMPLETED);
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
                contract.strategyName(),
                ComparisonSummary.from(contract.strategyName(), List.of(), List.of(issue)),
                List.of(),
                List.of(issue),
                List.of(),
                null
        );

        AgentAnalysis analysis = SpringAiAgentAnalyzer.toDomain(
                structuredResponse(input, Severity.WARNING),
                input,
                TokenUsage.zero()
        );

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(analysis.technicalRisks()).contains("\u043a\u0440\u0438\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0435 "
                + "\u043d\u0430\u0440\u0443\u0448\u0435\u043d\u0438\u044f");
    }

    @Test
    void springAiMappingRejectsFabricatedDiffExplanation() {
        AgentAnalysisInput input = inputForFixture("model-change");
        StructuredAgentAnalysis response = new StructuredAgentAnalysis(
                Severity.WARNING,
                "summary",
                "business impact",
                "technical risks",
                List.of("recommendation"),
                List.of(new DiffExplanation(
                        "D999",
                        "strategyResponse.fabricated",
                        Severity.WARNING,
                        "fabricated explanation"
                ))
        );

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(response, input, TokenUsage.zero()))
                .isInstanceOf(AgentAnalysisException.class)
                .hasMessageContaining("diffId is not in deterministic diffs");
    }

    @Test
    void springAiMappingAddsDeterministicExplanationForMissingHardCriticalDiff() {
        AgentAnalysisInput input = inputForFixture("mode-regression");
        List<DiffExplanation> modelExplanations = input.diffs().stream()
                .filter(diff -> !DeterministicSeverityCalculator.isHardCriticalDiff(diff))
                .map(AgentAnalyzerTest::explanation)
                .toList();
        StructuredAgentAnalysis response = structuredResponse(input, Severity.WARNING, modelExplanations);

        AgentAnalysis analysis = SpringAiAgentAnalyzer.toDomain(response, input, TokenUsage.zero());

        assertThat(input.diffs()).anyMatch(DeterministicSeverityCalculator::isHardCriticalDiff);
        assertThat(analysis.diffExplanations()).anySatisfy(explanation ->
                assertThat(explanation.severity()).isEqualTo(Severity.CRITICAL));
    }

    @Test
    void springAiMappingRejectsBlankRequiredText() {
        AgentAnalysisInput input = inputForFixture("model-change");
        StructuredAgentAnalysis response = new StructuredAgentAnalysis(
                Severity.WARNING,
                "",
                "business impact",
                "technical risks",
                List.of("recommendation"),
                explanations(input)
        );

        assertThatThrownBy(() -> SpringAiAgentAnalyzer.toDomain(response, input, TokenUsage.zero()))
                .isInstanceOf(AgentAnalysisException.class)
                .hasMessageContaining("summary is blank");
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

    private StructuredAgentAnalysis structuredResponse(AgentAnalysisInput input, Severity severity) {
        return structuredResponse(input, severity, explanations(input));
    }

    private StructuredAgentAnalysis structuredResponse(
            AgentAnalysisInput input,
            Severity severity,
            List<DiffExplanation> explanations
    ) {
        return new StructuredAgentAnalysis(
                severity,
                "summary",
                "business impact",
                "technical risks",
                List.of("recommendation"),
                explanations
        );
    }

    private static List<DiffExplanation> explanations(AgentAnalysisInput input) {
        return input.diffs().stream()
                .map(AgentAnalyzerTest::explanation)
                .toList();
    }

    private static DiffExplanation explanation(DiffEntry diff) {
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.WARNING,
                "explanation for " + diff.id()
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

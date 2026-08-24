package ru.sberbank.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sberbank.strategy_launches_analyzer.TestFixtures;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentRepairContext;
import ru.sberbank.strategy_launches_analyzer.dto.agent.DiffExplanation;
import ru.sberbank.strategy_launches_analyzer.dto.agent.GuardrailCorrection;
import ru.sberbank.strategy_launches_analyzer.dto.agent.GuardrailCorrectionType;
import ru.sberbank.strategy_launches_analyzer.dto.agent.RepairableAgentResponseReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonBasis;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffResult;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffEntry;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffType;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.LaunchSide;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractFieldContext;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;
import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssueType;
import ru.sberbank.strategy_launches_analyzer.dto.strategy.StrategyName;
import ru.sberbank.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import ru.sberbank.strategy_launches_analyzer.service.contract.ContractValidator;
import ru.sberbank.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContract;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import ru.sberbank.strategy_launches_analyzer.service.diff.StrategyDiffEngine;
import ru.sberbank.strategy_launches_analyzer.service.diff.DeterministicDiffIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.ArrayList;
import java.util.List;

import static ru.sberbank.strategy_launches_analyzer.TestIds.D999;
import static ru.sberbank.strategy_launches_analyzer.TestIds.REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAnalyzerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentAnalysisPostProcessor postProcessor = new DefaultAgentAnalysisPostProcessor();
    private StrategyContract contract;
    private StrategyDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        contract = new StrategyContractRegistry(new OpenApiStrategyContractLoader()).get(StrategyName.LGD_DIGITAL);
        diffEngine = new StrategyDiffEngine(new ContractValidator(), new DeterministicDiffIdGenerator());
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
        assertThat(prompt).contains("Apply the Severity contract");
        assertThat(prompt).doesNotContain("\"highestSeverity\"");
        assertThat(AgentPromptBuilder.SYSTEM_PROMPT).doesNotContain("LGD_DIGITAL");
        assertThat(AgentPromptBuilder.SYSTEM_PROMPT).contains("not as final business severity");
        assertThat(prompt).contains("\"strategyName\" : \"LGD_DIGITAL\"");
        assertThat(prompt).containsOnlyOnce("\"strategyName\"");
        assertThat(prompt).contains("\"format\" : \"double\"");
        assertThat(prompt).contains("LGD-\u043F\u043E\u0442\u0435\u0440\u0438 \u043F\u0440\u0438 \u0434\u0435\u0444\u043E\u043B\u0442\u0435 (%)");
        assertThat(prompt).contains("\u041C\u043E\u0434\u0435\u043B\u044C \u0440\u0430\u0441\u0447\u0435\u0442\u0430");
        assertThat(prompt).contains("LGD \u043F\u0440\u0438 \u044D\u043A\u043E\u043D\u043E\u043C\u0438\u0447\u0435\u0441\u043A\u043E\u043C \u0441\u043F\u0430\u0434\u0435 (%)");
        assertThat(prompt).doesNotContain("\u0420\u0435\u0436\u0438\u043C \u0440\u0430\u0441\u0447\u0435\u0442\u0430");
        assertThat(prompt).doesNotContain("\"mainLaunch\"");
        assertThat(prompt).doesNotContain("\"shadowLaunch\"");
    }

    @Test
    void promptCarriesTypeMismatchAndCoercedNumericChangeTogether() {
        BenchmarkLikeLaunches launches = benchmarkLaunches("metric-type-mismatch");
        DiffResult diffResult = diffEngine.compare(contract, REQUEST_ID, launches.main(), launches.shadow());
        AgentAnalysisInput input = input(diffResult);

        assertThat(input.diffs()).extracting(DiffEntry::type)
                .containsExactly(DiffType.TYPE_MISMATCH, DiffType.NUMERIC_VALUE_CHANGED);
        assertThat(input.diffs().get(1).comparisonBasis()).isEqualTo(ComparisonBasis.COERCED_NUMERIC);

        String prompt = new AgentPromptBuilder(objectMapper).buildUserPrompt(input);
        assertThat(prompt).contains("\"comparisonBasis\" : \"COERCED_NUMERIC\"");
        assertThat(prompt).contains("\"absoluteDelta\" : 2.3");
        assertThat(prompt).contains("\"relativeDeltaPercent\" : 12.7072");
    }

    @Test
    void repairPromptContainsOnlyStructuredFailureContextAndCompleteResponseInstruction() {
        AgentAnalysisInput input = inputForFixture("model-change");
        StructuredAgentAnalysis previous = structuredResponse(input, Severity.WARNING);
        AgentRepairContext repairContext = new AgentRepairContext(
                RepairableAgentResponseReason.CONTRACT_VIOLATION,
                List.of("summary is blank"),
                previous
        );

        String prompt = new AgentPromptBuilder(objectMapper).buildRepairPrompt(input, repairContext);

        assertThat(prompt).contains("CONTRACT_VIOLATION");
        assertThat(prompt).contains("summary is blank");
        assertThat(prompt).contains("Return the complete corrected object, not a patch");
        assertThat(prompt).contains("Previous parsed response");
        assertThat(prompt).contains("Normalized deterministic input");
        assertThat(prompt).contains("\"strategyName\" : \"LGD_DIGITAL\"");
        assertThat(prompt).doesNotContain("mainLaunch");
        assertThat(prompt).doesNotContain("shadowLaunch");
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
        assertThat(systemPrompt).contains("comparisonBasis=COERCED_NUMERIC");
        assertThat(systemPrompt).contains("original string remains contract-invalid");
        assertThat(systemPrompt).contains("never describe coercion as contract validation or automatic correction");
        assertThat(systemPrompt).contains("technicalRisks: Explain technical and contract risks");
        assertThat(systemPrompt).contains("contract/schema/type/nullability issues");
        assertThat(systemPrompt).contains("unknown fields");
        assertThat(systemPrompt).contains("serialization/mapping/integration mode regressions");
        assertThat(systemPrompt).contains("recommendations: Return concrete actionable follow-up actions");
        assertThat(systemPrompt).contains("blocking promotion for CRITICAL issues");
        assertThat(systemPrompt).contains("Return at most 10 recommendations");
        assertThat(systemPrompt).contains("Return exactly one diffExplanation for every diff");
        assertThat(systemPrompt).contains("copy its path verbatim");
        assertThat(systemPrompt).contains("never return a duplicate diffId");
        assertThat(systemPrompt).contains("without exceptions");
        assertThat(systemPrompt).contains("deterministicSeverity");
        assertThat(systemPrompt).doesNotContain("MAY omit hard-critical diffs");
        assertThat(systemPrompt).contains("Write summary, businessImpact, technicalRisks");
    }

    @Test
    void systemPromptDefinesOverallSeverityContract() {
        String systemPrompt = AgentPromptBuilder.SYSTEM_PROMPT;

        assertThat(systemPrompt).contains("Severity contract:");
        assertThat(systemPrompt).contains("INFO: Use only when there are no deterministic diffs");
        assertThat(systemPrompt).contains("no contract validation issues");
        assertThat(systemPrompt).contains("WARNING: Use when there are non-critical diffs");
        assertThat(systemPrompt).contains("warning-level contract issues");
        assertThat(systemPrompt).contains("no blocking contract/schema/type/nullability signal");
        assertThat(systemPrompt).contains("CRITICAL: Use when there is a critical contract validation issue");
        assertThat(systemPrompt).contains("hard-critical diff");
        assertThat(systemPrompt).contains(".mode or .type change");
        assertThat(systemPrompt).contains("required field missing");
        assertThat(systemPrompt).contains("type mismatch");
        assertThat(systemPrompt).contains("nullability violation");
        assertThat(systemPrompt).contains("payload-supported risk that should block promotion");
        assertThat(systemPrompt).contains("You may raise summary.deterministicSeverity");
        assertThat(systemPrompt).contains("never lower deterministic CRITICAL");
        assertThat(systemPrompt).contains("if any diffExplanation severity is CRITICAL");
        assertThat(systemPrompt).contains("overallSeverity must be CRITICAL");
    }

    @Test
    void structuredMappingKeepsHardTechnicalDiffCriticalWhenModelDowngradesIt() {
        AgentAnalysisInput input = inputForFixture("mode-regression");

        AgentPostProcessingResult processed = postProcessor.process(
                structuredResponse(input, Severity.WARNING),
                input,
                TokenUsage.zero()
        );
        AgentAnalysis analysis = processed.analysis();

        assertThat(analysis.status()).isEqualTo(AgentAnalysisStatus.COMPLETED);
        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(processed.corrections()).extracting(GuardrailCorrection::type)
                .contains(GuardrailCorrectionType.DIFF_SEVERITY_ESCALATED,
                        GuardrailCorrectionType.OVERALL_SEVERITY_CHANGED);
    }

    @Test
    void structuredMappingKeepsCriticalContractIssueCriticalWhenModelDowngradesIt() {
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
                ComparisonSummary.from(List.of(), List.of(issue)),
                List.of(),
                List.of(issue),
                List.of(),
                null
        );

        AgentPostProcessingResult processed = postProcessor.process(
                structuredResponse(input, Severity.WARNING),
                input,
                TokenUsage.zero()
        );
        AgentAnalysis analysis = processed.analysis();

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(analysis.technicalRisks()).contains("\u043a\u0440\u0438\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0435 "
                + "\u043d\u0430\u0440\u0443\u0448\u0435\u043d\u0438\u044f");
        assertThat(processed.corrections()).extracting(GuardrailCorrection::type)
                .contains(GuardrailCorrectionType.CRITICAL_CONTRACT_RISK_APPENDED,
                        GuardrailCorrectionType.OVERALL_SEVERITY_CHANGED);
    }

    @Test
    void bothSidesMissingRequiredFieldHasNoDiffButPreservesCriticalRisk() {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/main.json").deepCopy();
        JsonNode shadow = main.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) main.at("/strategyResponse/lgdData")).remove("lgd");
        ((com.fasterxml.jackson.databind.node.ObjectNode) shadow.at("/strategyResponse/lgdData")).remove("lgd");
        AgentAnalysisInput input = input(diffEngine.compare(contract, REQUEST_ID, main, shadow));

        AgentPostProcessingResult processed = postProcessor.process(
                structuredResponse(input, Severity.WARNING, List.of()), input, TokenUsage.zero()
        );

        assertThat(input.diffs()).isEmpty();
        assertThat(input.contractValidation()).hasSize(2);
        assertThat(processed.analysis().overallSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(processed.analysis().technicalRisks())
                .contains("критические нарушения: 2", "до promotion");
    }

    @Test
    void missingRequiredFieldGetsCriticalContractFallbackWhenModelOmitsExplanation() {
        AgentAnalysisInput input = inputForFixture("model-change", "main", "shadow");
        DiffEntry source = input.diffs().get(0);
        ContractIssue issue = new ContractIssue(
                "C777",
                LaunchSide.SHADOW,
                source.path(),
                ContractIssueType.REQUIRED_FIELD_MISSING,
                Severity.CRITICAL,
                "number, required=true",
                "missing",
                null,
                "Required field is missing."
        );
        DiffEntry criticalDiff = new DiffEntry(
                source.id(), source.path(), source.type(), source.category(), source.mainValue(), source.shadowValue(),
                source.absoluteDelta(), source.relativeDeltaPercent(), source.comparisonBasis(), Severity.CRITICAL
        );
        AgentAnalysisInput criticalInput = new AgentAnalysisInput(
                input.strategyName(),
                new ComparisonSummary(1, 1, 0, 0, 0, 1, true, Severity.CRITICAL),
                List.of(criticalDiff),
                List.of(issue),
                input.contractContext(),
                input.metadata()
        );

        AgentPostProcessingResult processed = postProcessor.process(
                structuredResponse(criticalInput, Severity.WARNING, List.of()), criticalInput, TokenUsage.zero()
        );

        assertThat(processed.analysis().diffExplanations()).singleElement().satisfies(explanation -> {
            assertThat(explanation.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(explanation.explanation())
                    .contains(source.path(), "SHADOW", "required=true", "блокировать promotion")
                    .doesNotContain("Некритичное изменение");
        });
        assertThat(processed.corrections()).extracting(GuardrailCorrection::type)
                .contains(GuardrailCorrectionType.CRITICAL_CONTRACT_EXPLANATION_ADDED);
    }

    @Test
    void structuredMappingEscalatesOverallSeverityWhenModelMarksNonCriticalDiffCritical() {
        AgentAnalysisInput input = inputForFixture("model-change");
        assertThat(input.summary().deterministicSeverity())
                .isNotEqualTo(Severity.CRITICAL);
        List<DiffExplanation> explanations = input.diffs().stream()
                .map(diff -> new DiffExplanation(diff.id(), diff.path(), Severity.CRITICAL, "explanation for " + diff.id()))
                .toList();
        StructuredAgentAnalysis response = structuredResponse(input, Severity.WARNING, explanations);

        AgentPostProcessingResult processed = postProcessor.process(response, input, TokenUsage.zero());
        AgentAnalysis analysis = processed.analysis();

        assertThat(analysis.overallSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void structuredMappingRejectsFabricatedDiffExplanation() {
        AgentAnalysisInput input = inputForFixture("model-change");
        StructuredAgentAnalysis response = new StructuredAgentAnalysis(
                Severity.WARNING,
                "summary",
                "business impact",
                "technical risks",
                List.of("recommendation"),
                List.of(new DiffExplanation(
                        D999,
                        "strategyResponse.fabricated",
                        Severity.WARNING,
                        "fabricated explanation"
                ))
        );

        assertThatThrownBy(() -> postProcessor.process(response, input, TokenUsage.zero()))
                .isInstanceOf(AgentAnalysisException.class)
                .hasMessageContaining("diffId is not in deterministic diffs");
    }

    @Test
    void structuredMappingRejectsNullDiffId() {
        AgentAnalysisInput input = inputForFixture("model-change");
        DiffEntry diff = input.diffs().get(0);
        StructuredAgentAnalysis response = structuredResponse(
                input,
                Severity.WARNING,
                List.of(new DiffExplanation(null, diff.path(), Severity.WARNING, "explanation"))
        );

        assertThatThrownBy(() -> postProcessor.process(response, input, TokenUsage.zero()))
                .isInstanceOf(AgentAnalysisException.class)
                .hasMessageContaining("diffId is null");
    }

    @Test
    void structuredMappingRejectsDuplicateDiffId() {
        AgentAnalysisInput input = inputForFixture("model-change");
        DiffExplanation explanation = explanation(input.diffs().get(0));
        StructuredAgentAnalysis response = structuredResponse(
                input,
                Severity.WARNING,
                List.of(explanation, explanation)
        );

        assertThatThrownBy(() -> postProcessor.process(response, input, TokenUsage.zero()))
                .isInstanceOf(AgentAnalysisException.class)
                .hasMessageContaining("diffId is duplicated");
    }

    @Test
    void structuredMappingAddsDeterministicExplanationForMissingHardCriticalDiff() {
        AgentAnalysisInput input = inputForFixture("mode-regression");
        List<DiffExplanation> modelExplanations = input.diffs().stream()
                .filter(diff -> diff.deterministicSeverity() != Severity.CRITICAL)
                .map(AgentAnalyzerTest::explanation)
                .toList();
        StructuredAgentAnalysis response = structuredResponse(input, Severity.WARNING, modelExplanations);

        AgentPostProcessingResult processed = postProcessor.process(response, input, TokenUsage.zero());
        AgentAnalysis analysis = processed.analysis();

        assertThat(input.diffs()).anyMatch(diff -> diff.deterministicSeverity() == Severity.CRITICAL);
        assertThat(analysis.diffExplanations()).anySatisfy(explanation ->
                assertThat(explanation.severity()).isEqualTo(Severity.CRITICAL));
        assertThat(processed.corrections()).extracting(GuardrailCorrection::type)
                .contains(GuardrailCorrectionType.HARD_CRITICAL_EXPLANATION_ADDED);
    }

    @Test
    void structuredMappingFillsOmittedNonCriticalDiffWithNeutralStubInsteadOfFailing() {
        AgentAnalysisInput input = inputForFixture("model-change");
        assertThat(input.diffs()).hasSizeGreaterThan(1);
        StructuredAgentAnalysis response = structuredResponse(
                input,
                Severity.WARNING,
                List.of(explanation(input.diffs().get(0)))
        );

        AgentPostProcessingResult processed = postProcessor.process(response, input, TokenUsage.zero());
        AgentAnalysis analysis = processed.analysis();

        assertThat(analysis.diffExplanations())
                .extracting(DiffExplanation::diffId)
                .containsExactlyInAnyOrderElementsOf(input.diffs().stream().map(DiffEntry::id).toList());
        assertThat(analysis.overallSeverity()).isEqualTo(Severity.WARNING);
        assertThat(processed.corrections()).extracting(GuardrailCorrection::type)
                .contains(GuardrailCorrectionType.NON_CRITICAL_EXPLANATION_ADDED);
    }

    @Test
    void structuredAnalysisJsonSchemaMarksFieldsAsRequired() {
        String schema = new BeanOutputConverter<>(StructuredAgentAnalysis.class).getJsonSchema();

        assertThat(schema).contains("required");
        assertThat(schema).contains("overallSeverity");
        assertThat(schema).contains("summary");
        assertThat(schema).contains("diffExplanations");
    }

    @Test
    void structuredAnalysisJsonSchemaRequiresExplanationForEveryDeterministicDiff() throws Exception {
        JsonNode schema = objectMapper.readTree(
                new BeanOutputConverter<>(StructuredAgentAnalysis.class).getJsonSchema()
        );

        assertThat(schema.path("properties").path("diffExplanations").path("description").asText())
                .contains("per deterministic diff")
                .doesNotContain("non-critical");
        assertThat(AgentPromptBuilder.SYSTEM_PROMPT)
                .contains("exactly one diffExplanation for every diff");
    }

    @Test
    void promptFingerprintsIncludeSchemaAndUseShortMetricLabels() {
        String normalHash = AgentPromptFingerprint.normalPromptHash(objectMapper);
        String repairHash = AgentPromptFingerprint.repairPromptHash(objectMapper);

        assertThat(normalHash).hasSize(64).isNotEqualTo(repairHash);
        assertThat(repairHash).hasSize(64);
        assertThat(AgentPromptFingerprint.metricHash(normalHash)).hasSize(12);
    }

    @Test
    void structuredAnalysisJsonSchemaKeepsRefsFreeOfSiblingKeywords() throws Exception {
        // Регрессия: @JsonPropertyDescription на enum-поле (Severity) вешает description рядом с $ref.
        // Строгий structured output это запрещает ("$ref cannot have keywords") → ошибка на каждом
        // вызове LLM → 500. Severity ссылается из двух полей, поэтому выносится в $defs/$ref.
        String schema = new BeanOutputConverter<>(StructuredAgentAnalysis.class).getJsonSchema();

        // Не вакуумно: enum действительно вынесен в $ref (иначе инвариант проверять нечего).
        assertThat(schema).contains("$ref");
        assertNoRefHasSiblings(objectMapper.readTree(schema), "$");
    }

    private static void assertNoRefHasSiblings(JsonNode node, String path) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            if (names.contains("$ref")) {
                assertThat(names)
                        .as("schema node %s with $ref must not carry sibling keywords (strict structured output)", path)
                        .containsExactly("$ref");
            }
            for (String name : names) {
                assertNoRefHasSiblings(node.get(name), path + "." + name);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                assertNoRefHasSiblings(node.get(i), path + "[" + i + "]");
            }
        }
    }

    @Test
    void structuredMappingRejectsBlankRequiredText() {
        AgentAnalysisInput input = inputForFixture("model-change");
        StructuredAgentAnalysis response = new StructuredAgentAnalysis(
                Severity.WARNING,
                "",
                "business impact",
                "technical risks",
                List.of("recommendation"),
                explanations(input)
        );

        assertThatThrownBy(() -> postProcessor.process(response, input, TokenUsage.zero()))
                .isInstanceOf(AgentAnalysisException.class)
                .hasMessageContaining("summary is blank");
    }

    private AgentAnalysisInput inputForFixture(String fixtureName) {
        return inputForFixture(fixtureName, "main", "shadow");
    }

    private AgentAnalysisInput inputForFixture(String fixtureName, String mainLaunchName, String shadowLaunchName) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/%s.json".formatted(fixtureName, mainLaunchName));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/%s.json".formatted(fixtureName, shadowLaunchName));
        DiffResult diffResult = diffEngine.compare(contract, REQUEST_ID, main, shadow);
        return input(diffResult);
    }

    private AgentAnalysisInput input(DiffResult diffResult) {
        ComparisonSummary summary = ComparisonSummary.from(
                diffResult.diffs(),
                diffResult.contractValidation()
        );
        return new AgentAnalysisInput(
                contract.strategyName(),
                summary,
                AgentInputNormalizer.normalizeDiffs(diffResult.diffs()),
                diffResult.contractValidation(),
                contractContext(diffResult),
                null
        );
    }

    private BenchmarkLikeLaunches benchmarkLaunches(String fixtureName) {
        return new BenchmarkLikeLaunches(
                TestFixtures.json(objectMapper, "evals/lgd-digital/%s/main.json".formatted(fixtureName)),
                TestFixtures.json(objectMapper, "evals/lgd-digital/%s/shadow.json".formatted(fixtureName))
        );
    }

    private record BenchmarkLikeLaunches(JsonNode main, JsonNode shadow) {
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

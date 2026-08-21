package com.github.denisspec989.strategy_launches_analyzer.service.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonBasis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DeterministicSeverityCalculator;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContract;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractField;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractValueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.StrategyContractDefinition;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.denisspec989.strategy_launches_analyzer.TestIds.OTHER_REQUEST_ID;
import static com.github.denisspec989.strategy_launches_analyzer.TestIds.REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyDiffEngineTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StrategyContract contract;
    private StrategyDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        contract = new StrategyContractRegistry(new OpenApiStrategyContractLoader()).get(StrategyName.LGD_DIGITAL);
        diffEngine = new StrategyDiffEngine(new ContractValidator(), new DeterministicDiffIdGenerator());
    }

    @Test
    void detectsModelAndMetricChanges() {
        DiffResult result = compareFixture("model-change");

        assertDiffsMatchExpected(result.diffs(), "model-change");
        assertThat(result.contractValidation()).isEmpty();
        assertThat(result.diffs()).hasSize(3);
        assertThat(result.diffs()).extracting(DiffEntry::id)
                .doesNotContainNull()
                .doesNotHaveDuplicates()
                .allSatisfy(id -> {
                    assertThat(id.version()).isEqualTo(5);
                    assertThat(id.variant()).isEqualTo(2);
                });
        assertThat(result.diffs().get(0).absoluteDelta()).isEqualByComparingTo(new BigDecimal("2.3"));
        assertThat(result.diffs().get(0).relativeDeltaPercent()).isEqualByComparingTo(new BigDecimal("12.7072"));
    }

    @Test
    void diffIdsAreStableForSameRequestAndDifferentAcrossRequests() {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/main.json");
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/shadow.json");

        List<UUID> first = diffEngine.compare(contract, REQUEST_ID, main, shadow).diffs().stream()
                .map(DiffEntry::id)
                .toList();
        List<UUID> repeated = diffEngine.compare(contract, REQUEST_ID, main, shadow).diffs().stream()
                .map(DiffEntry::id)
                .toList();
        List<UUID> anotherRequest = diffEngine.compare(contract, OTHER_REQUEST_ID, main, shadow).diffs().stream()
                .map(DiffEntry::id)
                .toList();

        assertThat(repeated).containsExactlyElementsOf(first);
        assertThat(anotherRequest).doesNotContainAnyElementsOf(first);
    }

    @Test
    void rejectsUuidCollisionAsInternalDiffEngineError() {
        DeterministicDiffIdGenerator collidingGenerator = new DeterministicDiffIdGenerator() {
            @Override
            public UUID generate(
                    UUID requestId,
                    String strategyName,
                    String path,
                    DiffType type,
                    DiffCategory category,
                    ComparisonBasis comparisonBasis
            ) {
                return REQUEST_ID;
            }
        };
        StrategyDiffEngine collidingEngine = new StrategyDiffEngine(new ContractValidator(), collidingGenerator);
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/main.json");
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/shadow.json");

        assertThatThrownBy(() -> collidingEngine.compare(contract, REQUEST_ID, main, shadow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Deterministic diff ID collision detected.");
    }

    @Test
    void detectsSuspiciousModeRegression() {
        DiffResult result = compareFixture("mode-regression");

        assertDiffsMatchExpected(result.diffs(), "mode-regression");
        assertThat(result.contractValidation()).isEmpty();
        assertThat(result.diffs())
                .singleElement()
                .satisfies(diff -> {
                    assertThat(diff.path()).isEqualTo("strategyResponse.calculationInfo.mode");
                    assertThat(diff.mainValue().asText()).isEqualTo("DEAL");
                    assertThat(diff.shadowValue().asText()).isEqualTo("DEA");
                    assertThat(diff.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
                });
    }

    @Test
    void detectsRemovedFieldAddedInShadowAsUnknownField() {
        DiffResult result = compareFixture("using-collateral-added");

        assertDiffsMatchExpected(result.diffs(), "using-collateral-added");
        assertThat(result.contractValidation())
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.side()).isEqualTo(LaunchSide.SHADOW);
                    assertThat(issue.path()).isEqualTo("strategyResponse.calculationInfo.usingCollateral");
                    assertThat(issue.type()).isEqualTo(ContractIssueType.UNKNOWN_FIELD);
                    assertThat(issue.expected()).contains("field declared");
                    assertThat(issue.actual()).isEqualTo("string");
                });
    }

    @Test
    void treatsEquivalentDecimalsAsEqual() {
        JsonNode main = objectMapper.createObjectNode()
                .set("strategyResponse", objectMapper.createObjectNode()
                        .set("lgdData", objectMapper.createObjectNode()
                                .put("lgd", 18.10)
                                .put("lgdModel", "model")
                                .put("lgdDt", 18.10)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) main.at("/strategyResponse"))
                .set("calculationInfo", objectMapper.createObjectNode()
                        .put("mode", "DEAL")
                        .put("type", "ONLINE")
                        .put("scenario", "SCENARIO")
                        .put("usedDefaultValue", true)
                        .put("defaultValueReason", "REASON"));
        JsonNode shadow = objectMapper.valueToTree(main);
        ((com.fasterxml.jackson.databind.node.ObjectNode) shadow.at("/strategyResponse/lgdData")).put("lgd", 18.1);
        ((com.fasterxml.jackson.databind.node.ObjectNode) shadow.at("/strategyResponse/lgdData")).put("lgdDt", 18.1);

        DiffResult result = diffEngine.compare(contract, REQUEST_ID, main, shadow);

        assertThat(result.diffs()).isEmpty();
        assertThat(result.contractValidation()).isEmpty();
    }

    @Test
    void requiredMissingIsCriticalForMainAndShadowAndProducesTwoIssuesWhenMissingOnBoth() {
        ObjectNode base = (ObjectNode) TestFixtures.json(
                objectMapper, "fixtures/lgd-digital/model-change/main.json"
        );
        for (LaunchSide missingSide : List.of(LaunchSide.MAIN, LaunchSide.SHADOW)) {
            ObjectNode main = base.deepCopy();
            ObjectNode shadow = base.deepCopy();
            ObjectNode target = missingSide == LaunchSide.MAIN ? main : shadow;
            ((ObjectNode) target.at("/strategyResponse/lgdData")).remove("lgd");

            DiffResult result = diffEngine.compare(contract, REQUEST_ID, main, shadow);

            assertThat(result.diffs()).singleElement().satisfies(diff -> {
                DiffType expectedType = missingSide == LaunchSide.MAIN
                        ? DiffType.FIELD_ADDED_IN_SHADOW
                        : DiffType.FIELD_MISSING_IN_SHADOW;
                assertThat(diff.type()).isEqualTo(expectedType);
                assertThat(diff.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
            });
            assertThat(result.contractValidation()).singleElement()
                    .satisfies(issue -> {
                        assertThat(issue.side()).isEqualTo(missingSide);
                        assertThat(issue.severity()).isEqualTo(Severity.CRITICAL);
                    });
        }

        ObjectNode mainMissing = base.deepCopy();
        ObjectNode shadowMissing = base.deepCopy();
        ((ObjectNode) mainMissing.at("/strategyResponse/lgdData")).remove("lgd");
        ((ObjectNode) shadowMissing.at("/strategyResponse/lgdData")).remove("lgd");
        DiffResult bothMissing = diffEngine.compare(contract, REQUEST_ID, mainMissing, shadowMissing);

        assertThat(bothMissing.diffs()).isEmpty();
        assertThat(bothMissing.contractValidation()).hasSize(2)
                .allSatisfy(issue -> {
                    assertThat(issue.type()).isEqualTo(ContractIssueType.REQUIRED_FIELD_MISSING);
                    assertThat(issue.severity()).isEqualTo(Severity.CRITICAL);
                });
    }

    @Test
    void optionalMissingRemainsWarning() {
        StrategyContract optionalContract = new StrategyContract(
                StrategyName.LGD_DIGITAL,
                new StrategyContractDefinition(
                        StrategyName.LGD_DIGITAL.name(),
                        "test",
                        "strategyResponse",
                        List.of(new ContractField(
                                "strategyResponse.optionalField",
                                ContractValueType.STRING,
                                null,
                                "0..1",
                                false,
                                DiffCategory.MODEL,
                                "Optional test field.",
                                null,
                                null
                        ))
                )
        );
        JsonNode main = objectMapper.createObjectNode().set(
                "strategyResponse",
                objectMapper.createObjectNode().put("optionalField", "present")
        );
        JsonNode shadow = objectMapper.createObjectNode().set(
                "strategyResponse",
                objectMapper.createObjectNode()
        );

        DiffResult result = diffEngine.compare(optionalContract, REQUEST_ID, main, shadow);

        assertThat(result.contractValidation()).isEmpty();
        assertThat(result.diffs()).singleElement().satisfies(diff -> {
            assertThat(diff.type()).isEqualTo(DiffType.FIELD_MISSING_IN_SHADOW);
            assertThat(diff.deterministicSeverity()).isEqualTo(Severity.WARNING);
        });
    }

    @Test
    void preservesRawJsonTypesInDiffsAndContractIssues() throws Exception {
        ObjectNode main = (ObjectNode) TestFixtures.json(
                objectMapper, "fixtures/lgd-digital/model-change/main.json"
        );
        ObjectNode shadow = main.deepCopy();
        ObjectNode shadowResponse = (ObjectNode) shadow.path("strategyResponse");
        shadowResponse.put("unknownNumber", 42.5);
        shadowResponse.put("unknownString", "42.5");
        shadowResponse.put("unknownBoolean", true);
        shadowResponse.set("unknownObject", objectMapper.readTree("{\"nested\":1}"));
        shadowResponse.set("unknownArray", objectMapper.readTree("[1,\"two\"]"));
        shadowResponse.putNull("unknownNull");

        DiffResult result = diffEngine.compare(contract, REQUEST_ID, main, shadow);
        Map<String, JsonNode> diffValues = result.diffs().stream()
                .filter(diff -> diff.path().contains("unknown"))
                .collect(java.util.stream.Collectors.toMap(DiffEntry::path, DiffEntry::shadowValue));
        Map<String, JsonNode> issueValues = result.contractValidation().stream()
                .filter(issue -> issue.path().contains("unknown"))
                .collect(java.util.stream.Collectors.toMap(
                        com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue::path,
                        com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue::actualValue
                ));

        assertOriginalTypes(diffValues);
        assertOriginalTypes(issueValues);
    }

    private static void assertOriginalTypes(Map<String, JsonNode> values) {
        assertThat(values).hasSize(6);
        assertThat(values.get("strategyResponse.unknownNumber").isNumber()).isTrue();
        assertThat(values.get("strategyResponse.unknownString").isTextual()).isTrue();
        assertThat(values.get("strategyResponse.unknownBoolean").isBoolean()).isTrue();
        assertThat(values.get("strategyResponse.unknownObject").isObject()).isTrue();
        assertThat(values.get("strategyResponse.unknownArray").isArray()).isTrue();
        assertThat(values.get("strategyResponse.unknownNull").isNull()).isTrue();
    }

    @Test
    void criticalContractIssueEscalatesOnlyAnExactPathMatch() {
        var criticalIssue = new com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue(
                "C001",
                LaunchSide.SHADOW,
                "strategyResponse.optionalField.child",
                ContractIssueType.TYPE_MISMATCH,
                Severity.CRITICAL,
                "string",
                "number",
                null,
                "Different path."
        );

        assertThat(DeterministicSeverityCalculator.resolveDiffSeverity(
                DiffType.FIELD_MISSING_IN_SHADOW,
                "strategyResponse.optionalField",
                List.of(criticalIssue)
        )).isEqualTo(Severity.WARNING);
        assertThat(DeterministicSeverityCalculator.resolveDiffSeverity(
                DiffType.FIELD_MISSING_IN_SHADOW,
                criticalIssue.path(),
                List.of(criticalIssue)
        )).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void reportsTypeMismatchAndCoercedNumericIncrease() throws Exception {
        DiffResult result = compareLgd(number("18.1"), text("20.4"));

        assertThat(result.diffs()).hasSize(2);
        assertThat(result.diffs()).extracting(DiffEntry::type)
                .containsExactly(DiffType.TYPE_MISMATCH, DiffType.NUMERIC_VALUE_CHANGED);
        assertThat(result.diffs()).extracting(DiffEntry::id).doesNotHaveDuplicates();
        DiffEntry numericDiff = result.diffs().get(1);
        assertThat(numericDiff.path()).isEqualTo("strategyResponse.lgdData.lgd");
        assertThat(numericDiff.absoluteDelta()).isEqualByComparingTo("2.3");
        assertThat(numericDiff.relativeDeltaPercent()).isEqualByComparingTo("12.7072");
        assertThat(numericDiff.comparisonBasis()).isEqualTo(ComparisonBasis.COERCED_NUMERIC);
        assertThat(numericDiff.mainValue().isNumber()).isTrue();
        assertThat(numericDiff.shadowValue().isTextual()).isTrue();
        assertThat(numericDiff.comparisonBasis()).isEqualTo(ComparisonBasis.COERCED_NUMERIC);

        ComparisonSummary summary = ComparisonSummary.from(
                result.diffs(),
                result.contractValidation()
        );
        assertThat(summary.totalDiffs()).isEqualTo(2);
        assertThat(summary.metricDiffs()).isEqualTo(2);
        assertThat(summary.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(result.contractValidation()).singleElement()
                .satisfies(issue -> assertThat(issue.side()).isEqualTo(LaunchSide.SHADOW));

        String serialized = objectMapper.writeValueAsString(numericDiff);
        assertThat(serialized).contains("\"comparisonBasis\":\"COERCED_NUMERIC\"");
    }

    @Test
    void reportsCoercedNumericDecreaseWhenMainIsString() {
        DiffResult result = compareLgd(text("20.4"), number("18.1"));

        assertThat(result.diffs()).extracting(DiffEntry::type)
                .containsExactly(DiffType.TYPE_MISMATCH, DiffType.NUMERIC_VALUE_CHANGED);
        assertThat(result.diffs().get(1).absoluteDelta()).isEqualByComparingTo("-2.3");
        assertThat(result.diffs().get(1).relativeDeltaPercent()).isEqualByComparingTo("-11.2745");
        assertThat(result.diffs().get(1).comparisonBasis()).isEqualTo(ComparisonBasis.COERCED_NUMERIC);
        assertThat(result.contractValidation()).singleElement()
                .satisfies(issue -> assertThat(issue.side()).isEqualTo(LaunchSide.MAIN));
    }

    @Test
    void comparesTwoContractInvalidNumericStrings() {
        DiffResult result = compareLgd(text("18.1"), text("20.4"));

        assertThat(result.diffs()).extracting(DiffEntry::type)
                .containsExactly(DiffType.TYPE_MISMATCH, DiffType.NUMERIC_VALUE_CHANGED);
        assertThat(result.diffs().get(1).absoluteDelta()).isEqualByComparingTo("2.3");
        assertThat(result.diffs().get(1).comparisonBasis()).isEqualTo(ComparisonBasis.COERCED_NUMERIC);
        assertThat(result.contractValidation()).hasSize(2);
    }

    @Test
    void trimsNumericStringBeforeDiagnosticComparison() {
        DiffResult result = compareLgd(number("18.1"), text(" 20.4 "));

        assertThat(result.diffs()).extracting(DiffEntry::type)
                .containsExactly(DiffType.TYPE_MISMATCH, DiffType.NUMERIC_VALUE_CHANGED);
        assertThat(result.diffs().get(1).absoluteDelta()).isEqualByComparingTo("2.3");
    }

    @Test
    void supportsNegativeAndExponentNumericStrings() {
        DiffResult result = compareLgd(text(" -2e1 "), text("1E1"));

        assertThat(result.diffs()).extracting(DiffEntry::type)
                .containsExactly(DiffType.TYPE_MISMATCH, DiffType.NUMERIC_VALUE_CHANGED);
        assertThat(result.diffs().get(1).absoluteDelta()).isEqualByComparingTo("30");
        assertThat(result.diffs().get(1).comparisonBasis()).isEqualTo(ComparisonBasis.COERCED_NUMERIC);
    }

    @Test
    void doesNotReportNumericChangeWhenCoercedValuesAreEqual() {
        DiffResult result = compareLgd(number("18.1"), text("18.10"));

        assertThat(result.diffs()).singleElement()
                .satisfies(diff -> assertThat(diff.type()).isEqualTo(DiffType.TYPE_MISMATCH));
    }

    @Test
    void rejectsAmbiguousOrNonNumericStringsForDiagnosticComparison() {
        for (String invalid : List.of("abc", "20,4", "20.4%", "NaN", "Infinity", "+20", ".5", "")) {
            DiffResult result = compareLgd(number("18.1"), text(invalid));

            assertThat(result.diffs())
                    .as("invalid numeric string %s", invalid)
                    .singleElement()
                    .satisfies(diff -> assertThat(diff.type()).isEqualTo(DiffType.TYPE_MISMATCH));
        }
    }

    @Test
    void omitsRelativeDeltaWhenCoercedMainValueIsZero() {
        DiffResult result = compareLgd(number("0"), text("5"));

        DiffEntry numericDiff = result.diffs().get(1);
        assertThat(numericDiff.type()).isEqualTo(DiffType.NUMERIC_VALUE_CHANGED);
        assertThat(numericDiff.absoluteDelta()).isEqualByComparingTo("5");
        assertThat(numericDiff.relativeDeltaPercent()).isNull();
        assertThat(numericDiff.comparisonBasis()).isEqualTo(ComparisonBasis.COERCED_NUMERIC);
    }

    @Test
    void omitsComparisonBasisForContractValidNumbers() throws Exception {
        DiffResult result = compareLgd(number("18.1"), number("20.4"));

        assertThat(result.diffs()).singleElement().satisfies(diff -> {
            assertThat(diff.type()).isEqualTo(DiffType.NUMERIC_VALUE_CHANGED);
            assertThat(diff.comparisonBasis()).isNull();
        });
        assertThat(objectMapper.writeValueAsString(result.diffs().get(0)))
                .doesNotContain("comparisonBasis");
    }

    private DiffResult compareFixture(String name) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/main.json".formatted(name));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/shadow.json".formatted(name));
        return diffEngine.compare(contract, REQUEST_ID, main, shadow);
    }

    private DiffResult compareLgd(JsonNode mainLgd, JsonNode shadowLgd) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/model-change/main.json");
        JsonNode shadow = main.deepCopy();
        ((ObjectNode) main.at("/strategyResponse/lgdData")).set("lgd", mainLgd);
        ((ObjectNode) shadow.at("/strategyResponse/lgdData")).set("lgd", shadowLgd);
        return diffEngine.compare(contract, REQUEST_ID, main, shadow);
    }

    private static JsonNode number(String value) {
        return DecimalNode.valueOf(new BigDecimal(value));
    }

    private JsonNode text(String value) {
        return objectMapper.getNodeFactory().textNode(value);
    }

    private void assertDiffsMatchExpected(List<DiffEntry> diffs, String fixtureName) {
        JsonNode expectedDiffs = TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/%s/expected-diff.json".formatted(fixtureName)
        );
        assertThat(diffs).hasSize(expectedDiffs.size());
        for (int i = 0; i < expectedDiffs.size(); i++) {
            JsonNode expected = expectedDiffs.get(i);
            DiffEntry actual = diffs.get(i);
            assertThat(actual.path()).isEqualTo(expected.get("path").asText());
            assertThat(actual.type()).isEqualTo(DiffType.valueOf(expected.get("type").asText()));
            assertThat(actual.category()).isEqualTo(DiffCategory.valueOf(expected.get("category").asText()));
            assertThat(actual.deterministicSeverity()).isIn(Severity.WARNING, Severity.CRITICAL);
        }
    }
}

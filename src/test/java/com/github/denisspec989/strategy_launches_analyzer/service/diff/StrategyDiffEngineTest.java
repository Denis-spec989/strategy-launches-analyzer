package com.github.denisspec989.strategy_launches_analyzer.service.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContract;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyDiffEngineTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StrategyContract contract;
    private StrategyDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        contract = new StrategyContractRegistry(new OpenApiStrategyContractLoader()).get(StrategyName.LGD_DIGITAL);
        diffEngine = new StrategyDiffEngine(new ContractValidator());
    }

    @Test
    void detectsModelAndMetricChanges() {
        DiffResult result = compareFixture("model-change");

        assertDiffsMatchExpected(result.diffs(), "model-change");
        assertThat(result.contractValidation()).isEmpty();
        assertThat(result.diffs()).hasSize(3);
        assertThat(result.diffs().get(0).absoluteDelta()).isEqualByComparingTo(new BigDecimal("2.3"));
        assertThat(result.diffs().get(0).relativeDeltaPercent()).isEqualByComparingTo(new BigDecimal("12.7072"));
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
                });
    }

    @Test
    void detectsRemovedFieldAddedInShadowAsUnknownField() {
        DiffResult result = compareFixture("using-collateral-added");

        assertDiffsMatchExpected(result.diffs(), "using-collateral-added");
        assertThat(result.diffs())
                .singleElement()
                .satisfies(diff -> {
                    assertThat(diff.mainValue()).isNull();
                    assertThat(diff.shadowValue()).isNull();
                    assertThat(diff.mainValueSummary()).isNull();
                    assertThat(diff.shadowValueSummary().jsonType()).isEqualTo("string");
                    assertThat(diff.shadowValueSummary().stringLength()).isEqualTo(1);
                    assertThat(diff.shadowValueSummary().preview()).isEqualTo("4");
                });
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
    void summarizesUnknownObjectDiffWithoutRawSubtree() {
        ObjectNode main = fixture().deepCopy();
        ObjectNode shadow = fixture().deepCopy();
        ((ObjectNode) shadow.at("/strategyResponse/calculationInfo"))
                .set("extra", objectMapper.createObjectNode()
                        .put("token", "secret-token")
                        .set("nested", objectMapper.createObjectNode().put("value", "hidden-value")));

        DiffResult result = diffEngine.compare(contract, main, shadow);

        assertThat(result.diffs())
                .singleElement()
                .satisfies(diff -> {
                    assertThat(diff.path()).isEqualTo("strategyResponse.calculationInfo.extra");
                    assertThat(diff.mainValue()).isNull();
                    assertThat(diff.shadowValue()).isNull();
                    assertThat(diff.shadowValueSummary().jsonType()).isEqualTo("object");
                    assertThat(diff.shadowValueSummary().objectFieldCount()).isEqualTo(2);
                    assertThat(diff.shadowValueSummary().preview()).contains("token", "nested");
                    assertThat(diff.shadowValueSummary().preview()).doesNotContain("secret-token", "hidden-value");
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

        DiffResult result = diffEngine.compare(contract, main, shadow);

        assertThat(result.diffs()).isEmpty();
        assertThat(result.contractValidation()).isEmpty();
    }

    private DiffResult compareFixture(String name) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/main.json".formatted(name));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/shadow.json".formatted(name));
        return diffEngine.compare(contract, main, shadow);
    }

    private ObjectNode fixture() {
        return (ObjectNode) TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/main.json"
        );
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
        }
    }
}

package com.github.denisspec989.strategy_launches_analyzer.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.contract.LgdDigitalContract;
import com.github.denisspec989.strategy_launches_analyzer.domain.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.domain.LaunchSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LgdDigitalDiffEngineTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private LgdDigitalDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        LgdDigitalContract contract = new LgdDigitalContract();
        diffEngine = new LgdDigitalDiffEngine(contract, new ContractValidator(contract));
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

        DiffResult result = diffEngine.compare(main, shadow);

        assertThat(result.diffs()).isEmpty();
        assertThat(result.contractValidation()).isEmpty();
    }

    private DiffResult compareFixture(String name) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/main.json".formatted(name));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/shadow.json".formatted(name));
        return diffEngine.compare(main, shadow);
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

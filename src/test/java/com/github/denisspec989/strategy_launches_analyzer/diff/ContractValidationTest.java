package com.github.denisspec989.strategy_launches_analyzer.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.denisspec989.strategy_launches_analyzer.TestFixtures;
import com.github.denisspec989.strategy_launches_analyzer.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.contract.LgdDigitalContract;
import com.github.denisspec989.strategy_launches_analyzer.domain.ContractIssueType;
import com.github.denisspec989.strategy_launches_analyzer.domain.LaunchSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ContractValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ContractValidator(new LgdDigitalContract());
    }

    @Test
    void reportsMissingRequiredField() {
        ObjectNode launch = fixture().deepCopy();
        ((ObjectNode) launch.at("/strategyResponse/lgdData")).remove("lgd");

        assertThat(validator.validate(launch, LaunchSide.SHADOW, new AtomicInteger(1)))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.type()).isEqualTo(ContractIssueType.REQUIRED_FIELD_MISSING);
                    assertThat(issue.path()).isEqualTo("strategyResponse.lgdData.lgd");
                    assertThat(issue.side()).isEqualTo(LaunchSide.SHADOW);
                });
    }

    @Test
    void allowsNullLgdModel() {
        ObjectNode launch = fixture().deepCopy();
        ((ObjectNode) launch.at("/strategyResponse/lgdData")).putNull("lgdModel");

        assertThat(validator.validate(launch, LaunchSide.MAIN, new AtomicInteger(1))).isEmpty();
    }

    @Test
    void allowsAbsentOptionalUsingCollateral() {
        assertThat(validator.validate(fixture(), LaunchSide.MAIN, new AtomicInteger(1))).isEmpty();
    }

    @Test
    void reportsUsingCollateralStringAsTypeMismatch() {
        JsonNode launch = TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/using-collateral-added/shadow.json"
        );

        assertThat(validator.validate(launch, LaunchSide.SHADOW, new AtomicInteger(1)))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.type()).isEqualTo(ContractIssueType.TYPE_MISMATCH);
                    assertThat(issue.path()).isEqualTo("strategyResponse.calculationInfo.usingCollateral");
                    assertThat(issue.actual()).isEqualTo("string");
                });
    }

    @Test
    void reportsUnknownFieldAtItsRootPath() {
        ObjectNode launch = fixture().deepCopy();
        ((ObjectNode) launch.at("/strategyResponse/calculationInfo"))
                .set("newContainer", objectMapper.createObjectNode().put("nested", "value"));

        assertThat(validator.validate(launch, LaunchSide.SHADOW, new AtomicInteger(1)))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.type()).isEqualTo(ContractIssueType.UNKNOWN_FIELD);
                    assertThat(issue.path()).isEqualTo("strategyResponse.calculationInfo.newContainer");
                });
    }

    private ObjectNode fixture() {
        return (ObjectNode) TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/model-change/main.json"
        );
    }
}

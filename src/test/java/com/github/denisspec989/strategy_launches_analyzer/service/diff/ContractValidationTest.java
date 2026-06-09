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
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.LaunchSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ContractValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private StrategyContract contract;
    private ContractValidator validator;

    @BeforeEach
    void setUp() {
        contract = new StrategyContractRegistry(new OpenApiStrategyContractLoader()).get(StrategyName.LGD_DIGITAL);
        validator = new ContractValidator();
    }

    @Test
    void reportsMissingRequiredField() {
        ObjectNode launch = fixture().deepCopy();
        ((ObjectNode) launch.at("/strategyResponse/lgdData")).remove("lgd");

        assertThat(validator.validate(contract, launch, LaunchSide.SHADOW, new AtomicInteger(1)))
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

        assertThat(validator.validate(contract, launch, LaunchSide.MAIN, new AtomicInteger(1))).isEmpty();
    }

    @Test
    void validatesFixtureWithoutRemovedUsingCollateral() {
        assertThat(validator.validate(contract, fixture(), LaunchSide.MAIN, new AtomicInteger(1))).isEmpty();
    }

    @Test
    void reportsUsingCollateralAsUnknownField() {
        JsonNode launch = TestFixtures.json(
                objectMapper,
                "fixtures/lgd-digital/using-collateral-added/shadow.json"
        );

        assertThat(validator.validate(contract, launch, LaunchSide.SHADOW, new AtomicInteger(1)))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.type()).isEqualTo(ContractIssueType.UNKNOWN_FIELD);
                    assertThat(issue.path()).isEqualTo("strategyResponse.calculationInfo.usingCollateral");
                    assertThat(issue.actual()).isEqualTo("string");
                });
    }

    @Test
    void reportsUnknownFieldAtItsRootPath() {
        ObjectNode launch = fixture().deepCopy();
        ((ObjectNode) launch.at("/strategyResponse/calculationInfo"))
                .set("newContainer", objectMapper.createObjectNode().put("nested", "value"));

        assertThat(validator.validate(contract, launch, LaunchSide.SHADOW, new AtomicInteger(1)))
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

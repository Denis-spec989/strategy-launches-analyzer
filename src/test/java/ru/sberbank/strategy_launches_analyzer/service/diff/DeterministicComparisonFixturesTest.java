package ru.sberbank.strategy_launches_analyzer.service.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sberbank.strategy_launches_analyzer.TestFixtures;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffResult;
import ru.sberbank.strategy_launches_analyzer.dto.strategy.StrategyName;
import ru.sberbank.strategy_launches_analyzer.service.contract.ContractValidator;
import ru.sberbank.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContract;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContractRegistry;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicComparisonFixturesTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StrategyContract contract = new StrategyContractRegistry(new OpenApiStrategyContractLoader())
            .get(StrategyName.LGD_DIGITAL);
    private final StrategyDiffEngine diffEngine = new StrategyDiffEngine(
            new ContractValidator(),
            new DeterministicDiffIdGenerator()
    );

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "calculation-context-change",
            "identical-basic",
            "identical-null-model",
            "metric-decrease",
            "metric-increase",
            "metric-main-zero",
            "metric-scale-equivalent",
            "metric-type-mismatch",
            "mode-critical",
            "model-only-change",
            "model-null-transition",
            "multi-diff-critical",
            "multi-diff-warning",
            "nullability-violation",
            "unknown-comment-field",
            "required-missing-shadow",
            "type-critical",
            "unknown-object-array",
            "unknown-scalar"
    })
    void matchesCapturedDeterministicResult(String scenario) {
        String root = "fixtures/lgd-digital/" + scenario + "/";
        JsonNode main = TestFixtures.json(objectMapper, root + "main.json");
        JsonNode shadow = TestFixtures.json(objectMapper, root + "shadow.json");
        JsonNode expected = TestFixtures.json(objectMapper, root + "expected-result.json");

        DiffResult result = diffEngine.compare(contract, REQUEST_ID, main, shadow);
        ObjectNode actual = objectMapper.createObjectNode();
        actual.set("diffs", objectMapper.valueToTree(result.diffs()));
        actual.set("contractValidation", objectMapper.valueToTree(result.contractValidation()));
        actual.set("summary", objectMapper.valueToTree(
                ComparisonSummary.from(result.diffs(), result.contractValidation())
        ));

        assertThat(actual.toString()).isEqualTo(expected.toString());
    }
}

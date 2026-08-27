package ru.sberbank.strategy_launches_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.sberbank.strategy_launches_analyzer.TestFixtures;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import ru.sberbank.strategy_launches_analyzer.dto.api.LaunchMetadata;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.dto.strategy.StrategyName;
import ru.sberbank.strategy_launches_analyzer.exceptions.BadRequestException;
import ru.sberbank.strategy_launches_analyzer.service.contract.ContractValidator;
import ru.sberbank.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import ru.sberbank.strategy_launches_analyzer.service.diff.DeterministicDiffIdGenerator;
import ru.sberbank.strategy_launches_analyzer.service.diff.StrategyDiffEngine;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompareStrategyLaunchesUseCaseTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final StrategyContractRegistry registry = new StrategyContractRegistry(new OpenApiStrategyContractLoader());
    private final StrategyDiffEngine diffEngine = new StrategyDiffEngine(
            new ContractValidator(),
            new DeterministicDiffIdGenerator()
    );

    @Test
    void returnsDeterministicComparisonWithContractMetadata() {
        CompareStrategyLaunchesUseCase useCase =
                new CompareStrategyLaunchesUseCase(diffEngine, registry, 100_000, 100);
        CompareStrategyRequest request = request("model-change");

        CompareStrategyResponse response = useCase.compare(request);
        JsonNode responseJson = objectMapper.valueToTree(response);

        assertThat(response.contractVersion())
                .isEqualTo(registry.get(StrategyName.LGD_DIGITAL).version())
                .isNotBlank();
        assertThat(response.analyzedAt()).isNotNull();
        assertThat(response.metadata()).isEqualTo(request.metadata());
        assertThat(response.diffs()).hasSize(3).allSatisfy(diff ->
                assertThat(diff.deterministicSeverity()).isIn(Severity.WARNING, Severity.CRITICAL));
        assertThat(response.summary().deterministicSeverity()).isEqualTo(Severity.WARNING);
        assertThat(responseJson.size()).isEqualTo(7);
    }

    @Test
    void rejectsLaunchPayloadExceedingNodeLimit() {
        CompareStrategyLaunchesUseCase useCase =
                new CompareStrategyLaunchesUseCase(diffEngine, registry, 5, 100);

        assertThatThrownBy(() -> useCase.compare(request("model-change")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("node limit");
    }

    @Test
    void rejectsLaunchPayloadExceedingDepthLimit() {
        CompareStrategyLaunchesUseCase useCase =
                new CompareStrategyLaunchesUseCase(diffEngine, registry, 100_000, 2);

        assertThatThrownBy(() -> useCase.compare(request("model-change")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nesting depth");
    }

    private CompareStrategyRequest request(String fixture) {
        JsonNode main = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/main.json".formatted(fixture));
        JsonNode shadow = TestFixtures.json(objectMapper, "fixtures/lgd-digital/%s/shadow.json".formatted(fixture));
        LaunchMetadata metadata = new LaunchMetadata(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "MAIN-1",
                "SHADOW-1",
                Instant.parse("2026-06-04T11:00:00Z"),
                Instant.parse("2026-06-04T11:01:00Z"),
                java.util.Map.of("source", "test")
        );
        return new CompareStrategyRequest(StrategyName.LGD_DIGITAL, main, shadow, metadata);
    }
}

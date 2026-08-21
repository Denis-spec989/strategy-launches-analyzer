package com.github.denisspec989.strategy_launches_analyzer.service.diff;

import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonBasis;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.denisspec989.strategy_launches_analyzer.TestIds.OTHER_REQUEST_ID;
import static com.github.denisspec989.strategy_launches_analyzer.TestIds.REQUEST_ID;
import static org.assertj.core.api.Assertions.assertThat;

class DeterministicDiffIdGeneratorTest {
    private final DeterministicDiffIdGenerator generator = new DeterministicDiffIdGenerator();

    @Test
    void generatesStableUuidV5IndependentOfInvocationOrder() {
        UUID expected = generate(REQUEST_ID, DiffType.TYPE_MISMATCH, null);

        generate(REQUEST_ID, DiffType.NUMERIC_VALUE_CHANGED, ComparisonBasis.COERCED_NUMERIC);
        UUID repeated = generate(REQUEST_ID, DiffType.TYPE_MISMATCH, null);

        assertThat(repeated).isEqualTo(expected);
        assertThat(expected.version()).isEqualTo(5);
        assertThat(expected.variant()).isEqualTo(2);
    }

    @Test
    void namespaceAndSemanticIdentityParticipateInUuid() {
        UUID typeMismatch = generate(REQUEST_ID, DiffType.TYPE_MISMATCH, null);
        UUID numeric = generate(
                REQUEST_ID,
                DiffType.NUMERIC_VALUE_CHANGED,
                ComparisonBasis.COERCED_NUMERIC
        );
        UUID anotherRequest = generate(OTHER_REQUEST_ID, DiffType.TYPE_MISMATCH, null);

        assertThat(typeMismatch).isNotEqualTo(numeric).isNotEqualTo(anotherRequest);
    }

    private UUID generate(UUID requestId, DiffType type, ComparisonBasis comparisonBasis) {
        return generator.generate(
                requestId,
                StrategyName.LGD_DIGITAL.name(),
                "strategyResponse.lgdData.lgd",
                type,
                DiffCategory.METRIC,
                comparisonBasis
        );
    }
}

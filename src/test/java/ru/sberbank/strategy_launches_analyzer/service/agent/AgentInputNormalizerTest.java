package ru.sberbank.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sberbank.strategy_launches_analyzer.dto.api.LaunchMetadata;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffCategory;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffEntry;
import ru.sberbank.strategy_launches_analyzer.dto.comparison.DiffType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ru.sberbank.strategy_launches_analyzer.TestIds.D001;
import static org.assertj.core.api.Assertions.assertThat;

class AgentInputNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void collapsesRawObjectSubtreeIntoCompactDescriptor() throws Exception {
        JsonNode subtree = objectMapper.readTree("{\"score\":0.1,\"flag\":true}");
        DiffEntry diff = new DiffEntry(D001, "strategyResponse.extra", DiffType.FIELD_ADDED_IN_SHADOW,
                DiffCategory.CONTRACT_TECHNICAL, null, subtree, null, null,
                Severity.WARNING);

        DiffEntry normalized = AgentInputNormalizer.normalizeDiffs(List.of(diff)).get(0);

        JsonNode shadowValue = normalized.shadowValue();
        assertThat(shadowValue.isObject()).isFalse();
        assertThat(shadowValue.asText()).startsWith("object(size=2):");
        assertThat(shadowValue.asText()).doesNotContain("\n");
    }

    @Test
    void keepsScalarValuesUnchanged() {
        JsonNode scalar = objectMapper.getNodeFactory().textNode("DEAL");
        DiffEntry diff = new DiffEntry(D001, "strategyResponse.lgdData.mode", DiffType.STRING_VALUE_CHANGED,
                DiffCategory.CONTRACT_TECHNICAL, scalar, scalar, null, null,
                Severity.CRITICAL);

        DiffEntry normalized = AgentInputNormalizer.normalizeDiffs(List.of(diff)).get(0);

        assertThat(normalized).isSameAs(diff);
        assertThat(normalized.shadowValue().asText()).isEqualTo("DEAL");
    }

    @Test
    void truncatesLargeObjectPreview() throws Exception {
        StringBuilder big = new StringBuilder("{");
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                big.append(",");
            }
            big.append("\"k").append(i).append("\":\"").append(i).append("\"");
        }
        big.append("}");
        JsonNode subtree = objectMapper.readTree(big.toString());
        DiffEntry diff = new DiffEntry(D001, "strategyResponse.extra", DiffType.FIELD_ADDED_IN_SHADOW,
                DiffCategory.CONTRACT_TECHNICAL, null, subtree, null, null,
                Severity.CRITICAL);

        DiffEntry normalized = AgentInputNormalizer.normalizeDiffs(List.of(diff)).get(0);

        assertThat(normalized.shadowValue().asText()).startsWith("object(size=100):");
        assertThat(normalized.deterministicSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(normalized.shadowValue().asText()).endsWith("…");
    }

    @Test
    void dropsClientAttributesButKeepsIdentifiers() {
        UUID requestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant mainLaunchDt = Instant.parse("2026-06-04T11:00:00Z");
        Instant shadowLaunchDt = Instant.parse("2026-06-04T11:01:00Z");
        LaunchMetadata metadata = new LaunchMetadata(requestId, "MAIN-1", "SHADOW-1",
                mainLaunchDt, shadowLaunchDt,
                Map.of("secret", "value"));

        LaunchMetadata sanitized = AgentInputNormalizer.withoutAttributes(metadata);

        assertThat(sanitized.attributes()).isNull();
        assertThat(sanitized.requestId()).isEqualTo(requestId);
        assertThat(sanitized.mainLaunchId()).isEqualTo("MAIN-1");
        assertThat(sanitized.shadowLaunchId()).isEqualTo("SHADOW-1");
        assertThat(sanitized.mainLaunchDt()).isEqualTo(mainLaunchDt);
        assertThat(sanitized.shadowLaunchDt()).isEqualTo(shadowLaunchDt);
    }

}

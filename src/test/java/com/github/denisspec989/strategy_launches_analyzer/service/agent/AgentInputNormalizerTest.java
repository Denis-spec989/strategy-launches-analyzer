package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.LaunchMetadata;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffCategory;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInputNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void collapsesRawObjectSubtreeIntoCompactDescriptor() throws Exception {
        JsonNode subtree = objectMapper.readTree("{\"score\":0.1,\"flag\":true}");
        DiffEntry diff = new DiffEntry("D001", "strategyResponse.extra", DiffType.FIELD_ADDED_IN_SHADOW,
                DiffCategory.CONTRACT_TECHNICAL, null, subtree, null, null, "Undeclared subtree.");

        DiffEntry normalized = AgentInputNormalizer.normalizeDiffs(List.of(diff)).get(0);

        JsonNode shadowValue = normalized.shadowValue();
        assertThat(shadowValue.isObject()).isFalse();
        assertThat(shadowValue.asText()).startsWith("object(size=2):");
        assertThat(shadowValue.asText()).doesNotContain("\n");
    }

    @Test
    void keepsScalarValuesUnchanged() {
        JsonNode scalar = objectMapper.getNodeFactory().textNode("DEAL");
        DiffEntry diff = new DiffEntry("D001", "strategyResponse.lgdData.mode", DiffType.STRING_VALUE_CHANGED,
                DiffCategory.CONTRACT_TECHNICAL, scalar, scalar, null, null, "Mode changed.");

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
        DiffEntry diff = new DiffEntry("D001", "strategyResponse.extra", DiffType.FIELD_ADDED_IN_SHADOW,
                DiffCategory.CONTRACT_TECHNICAL, null, subtree, null, null, "Big undeclared subtree.");

        DiffEntry normalized = AgentInputNormalizer.normalizeDiffs(List.of(diff)).get(0);

        assertThat(normalized.shadowValue().asText()).startsWith("object(size=100):");
        assertThat(normalized.shadowValue().asText()).endsWith("…");
    }

    @Test
    void dropsClientAttributesButKeepsIdentifiers() {
        LaunchMetadata metadata = new LaunchMetadata("REQ-1", "MAIN-1", "SHADOW-1", null,
                Map.of("secret", "value"));

        LaunchMetadata sanitized = AgentInputNormalizer.withoutAttributes(metadata);

        assertThat(sanitized.attributes()).isNull();
        assertThat(sanitized.requestId()).isEqualTo("REQ-1");
        assertThat(sanitized.mainLaunchId()).isEqualTo("MAIN-1");
        assertThat(sanitized.shadowLaunchId()).isEqualTo("SHADOW-1");
    }
}

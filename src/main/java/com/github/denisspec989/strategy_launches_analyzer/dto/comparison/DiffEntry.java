package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.JsonValueSummary;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiffEntry(
        String id,
        String path,
        DiffType type,
        DiffCategory category,
        JsonNode mainValue,
        JsonNode shadowValue,
        JsonValueSummary mainValueSummary,
        JsonValueSummary shadowValueSummary,
        BigDecimal absoluteDelta,
        BigDecimal relativeDeltaPercent,
        String description
) {
    public DiffEntry(
            String id,
            String path,
            DiffType type,
            DiffCategory category,
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal absoluteDelta,
            BigDecimal relativeDeltaPercent,
            String description
    ) {
        this(
                id,
                path,
                type,
                category,
                mainValue,
                shadowValue,
                null,
                null,
                absoluteDelta,
                relativeDeltaPercent,
                description
        );
    }
}

package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiffEntry(
        String id,
        String path,
        DiffType type,
        DiffCategory category,
        JsonNode mainValue,
        JsonNode shadowValue,
        BigDecimal absoluteDelta,
        BigDecimal relativeDeltaPercent,
        ComparisonBasis comparisonBasis,
        Severity deterministicSeverity,
        String description
) {
    public DiffEntry {
        if (deterministicSeverity == null || deterministicSeverity == Severity.INFO) {
            throw new IllegalArgumentException("Diff deterministicSeverity must be WARNING or CRITICAL.");
        }
    }

    public DiffEntry(
            String id,
            String path,
            DiffType type,
            DiffCategory category,
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal absoluteDelta,
            BigDecimal relativeDeltaPercent,
            Severity deterministicSeverity,
            String description
    ) {
        this(
                id,
                path,
                type,
                category,
                mainValue,
                shadowValue,
                absoluteDelta,
                relativeDeltaPercent,
                null,
                deterministicSeverity,
                description
        );
    }
}

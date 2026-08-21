package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiffEntry(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        String path,
        DiffType type,
        DiffCategory category,
        JsonNode mainValue,
        JsonNode shadowValue,
        BigDecimal absoluteDelta,
        BigDecimal relativeDeltaPercent,
        ComparisonBasis comparisonBasis,
        Severity deterministicSeverity
) {
    public DiffEntry {
        Objects.requireNonNull(id, "Diff id must not be null.");
        if (deterministicSeverity == null || deterministicSeverity == Severity.INFO) {
            throw new IllegalArgumentException("Diff deterministicSeverity must be WARNING or CRITICAL.");
        }
    }

    public DiffEntry(
            UUID id,
            String path,
            DiffType type,
            DiffCategory category,
            JsonNode mainValue,
            JsonNode shadowValue,
            BigDecimal absoluteDelta,
            BigDecimal relativeDeltaPercent,
            Severity deterministicSeverity
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
                deterministicSeverity
        );
    }
}

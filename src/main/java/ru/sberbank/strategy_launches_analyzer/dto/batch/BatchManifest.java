package ru.sberbank.strategy_launches_analyzer.dto.batch;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BatchManifest(
        @JsonProperty(required = true)
        String formatVersion,
        @JsonProperty(required = true)
        UUID batchId,
        @JsonProperty(required = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant startedAt,
        @JsonProperty(required = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant completedAt,
        @JsonProperty(required = true)
        int inputItems,
        @JsonProperty(required = true)
        int completedItems,
        @JsonProperty(required = true)
        int failedItems,
        @JsonProperty(required = true)
        int unchangedItems,
        @JsonProperty(required = true)
        int reportedItems,
        @JsonProperty(required = true)
        Map<String, Integer> severityCounts,
        @JsonProperty(required = true)
        long totalDiffs,
        @JsonProperty(required = true)
        long totalContractValidationIssues,
        @JsonProperty(required = true)
        List<BatchManifestFile> files
) {
    public static final String CURRENT_FORMAT_VERSION = "1.3";
}

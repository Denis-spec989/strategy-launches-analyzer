package ru.sberbank.strategy_launches_analyzer.dto.batch;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchManifestFile(
        @JsonProperty(required = true)
        String name,
        @JsonProperty(required = true)
        long sizeBytes,
        @JsonProperty(required = true)
        String sha256
) {
}

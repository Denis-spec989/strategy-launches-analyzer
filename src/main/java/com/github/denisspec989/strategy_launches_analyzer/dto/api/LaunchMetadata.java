package com.github.denisspec989.strategy_launches_analyzer.dto.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LaunchMetadata(
        @NotNull(message = "metadata.requestId is required.")
        @JsonDeserialize(using = CanonicalUuidDeserializer.class)
        @Schema(
                format = "uuid",
                pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
        UUID requestId,
        String mainLaunchId,
        String shadowLaunchId,
        @NotNull(message = "metadata.mainLaunchDt is required.")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant mainLaunchDt,
        @NotNull(message = "metadata.shadowLaunchDt is required.")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant shadowLaunchDt,
        Map<String, String> attributes
) {
}

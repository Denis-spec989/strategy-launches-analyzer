package ru.sberbank.strategy_launches_analyzer.service.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BatchItemParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void acceptsCanonicalUuidAndRejectsEveryLaterOccurrenceAsDuplicate() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            BatchItemParser parser = new BatchItemParser(objectMapper, validatorFactory.getValidator());
            HashSet<UUID> seen = new HashSet<>();
            String requestId = "11111111-1111-1111-1111-111111111111";

            ParsedBatchItem first = parser.parse(line(1, request(requestId)), seen);
            ParsedBatchItem duplicate = parser.parse(line(2, request(requestId)), seen);

            assertThat(first.valid()).isTrue();
            assertThat(first.request().metadata().requestId()).isEqualTo(UUID.fromString(requestId));
            assertThat(duplicate.valid()).isFalse();
            assertThat(duplicate.failure().error().code()).isEqualTo(BatchItemErrorCode.DUPLICATE_REQUEST_ID);
        }
    }

    @Test
    void rejectsNonCanonicalUuidAsItemValidationError() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            BatchItemParser parser = new BatchItemParser(objectMapper, validatorFactory.getValidator());

            ParsedBatchItem parsed = parser.parse(
                    line(1, request("11111111-1111-1111-1111-11111111111A")),
                    new HashSet<>()
            );

            assertThat(parsed.valid()).isFalse();
            assertThat(parsed.failure().error().code()).isEqualTo(BatchItemErrorCode.VALIDATION_ERROR);
            assertThat(parsed.failure().error().message()).contains("canonical lowercase UUID");
        }
    }

    private static BatchInputLine line(int sequence, String json) {
        return new BatchInputLine(sequence, sequence, json.getBytes(StandardCharsets.UTF_8));
    }

    private static String request(String requestId) {
        return """
                {"strategy":"LGD_DIGITAL","mainLaunch":{},"shadowLaunch":{},"metadata":{
                  "requestId":"%s",
                  "mainLaunchDt":"2026-08-27T10:00:00Z",
                  "shadowLaunchDt":"2026-08-27T10:01:00Z"
                }}
                """.formatted(requestId).replaceAll("\\R", "");
    }
}

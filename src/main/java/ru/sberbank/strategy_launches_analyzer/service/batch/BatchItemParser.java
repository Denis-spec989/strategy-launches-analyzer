package ru.sberbank.strategy_launches_analyzer.service.batch;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemErrorCode;
import ru.sberbank.strategy_launches_analyzer.dto.batch.BatchItemResult;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class BatchItemParser {
    private static final int MAX_ERROR_MESSAGE_CHARS = 500;

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public BatchItemParser(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public ParsedBatchItem parse(BatchInputLine line, Set<UUID> seenRequestIds) {
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(line.content()))
                    .toString();
        } catch (CharacterCodingException ex) {
            return failed(line, null, BatchItemErrorCode.INVALID_ENCODING,
                    "NDJSON line is not valid UTF-8.");
        }

        CompareStrategyRequest request;
        try {
            request = objectMapper.readValue(json, CompareStrategyRequest.class);
        } catch (JsonParseException ex) {
            return failed(line, null, BatchItemErrorCode.MALFORMED_JSON,
                    "Malformed JSON: " + safeMessage(ex.getOriginalMessage()));
        } catch (JsonProcessingException ex) {
            return failed(line, null, BatchItemErrorCode.VALIDATION_ERROR,
                    "Request cannot be deserialized: " + safeMessage(ex.getOriginalMessage()));
        }

        UUID requestId = request.metadata() == null ? null : request.metadata().requestId();
        Set<ConstraintViolation<CompareStrategyRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .distinct()
                    .collect(Collectors.joining("; "));
            return failed(line, requestId, request, BatchItemErrorCode.VALIDATION_ERROR, message);
        }
        if (!seenRequestIds.add(requestId)) {
            return failed(
                    line,
                    requestId,
                    request,
                    BatchItemErrorCode.DUPLICATE_REQUEST_ID,
                    "metadata.requestId is duplicated within the batch."
            );
        }
        return new ParsedBatchItem(line, request, null);
    }

    private static ParsedBatchItem failed(
            BatchInputLine line,
            UUID requestId,
            BatchItemErrorCode code,
            String message
    ) {
        return failed(line, requestId, null, code, message);
    }

    private static ParsedBatchItem failed(
            BatchInputLine line,
            UUID requestId,
            CompareStrategyRequest sourceRequest,
            BatchItemErrorCode code,
            String message
    ) {
        return new ParsedBatchItem(
                line,
                null,
                BatchItemResult.failed(
                        line.sequence(),
                        line.lineNumber(),
                        requestId,
                        sourceRequest,
                        code,
                        message
                )
        );
    }

    private static String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "unknown parsing error.";
        }
        String singleLine = value.replaceAll("\\s+", " ").strip();
        if (singleLine.length() > MAX_ERROR_MESSAGE_CHARS) {
            return singleLine.substring(0, MAX_ERROR_MESSAGE_CHARS) + "...";
        }
        return singleLine;
    }
}

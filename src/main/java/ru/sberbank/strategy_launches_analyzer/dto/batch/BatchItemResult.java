package ru.sberbank.strategy_launches_analyzer.dto.batch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import ru.sberbank.strategy_launches_analyzer.dto.api.CompareStrategyResponse;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchItemResult(
        @JsonProperty(required = true)
        int sequence,
        @JsonProperty(required = true)
        int lineNumber,
        UUID requestId,
        @JsonProperty(required = true)
        BatchItemStatus status,
        CompareStrategyResponse response,
        BatchItemError error,
        @JsonIgnore
        CompareStrategyRequest sourceRequest
) {
    public static BatchItemResult completed(
            int sequence,
            int lineNumber,
            UUID requestId,
            CompareStrategyResponse response
    ) {
        return new BatchItemResult(
                sequence,
                lineNumber,
                requestId,
                BatchItemStatus.COMPLETED,
                response,
                null,
                null
        );
    }

    public static BatchItemResult failed(
            int sequence,
            int lineNumber,
            UUID requestId,
            BatchItemErrorCode code,
            String message
    ) {
        return new BatchItemResult(
                sequence,
                lineNumber,
                requestId,
                BatchItemStatus.FAILED,
                null,
                new BatchItemError(code, message),
                null
        );
    }

    public static BatchItemResult failed(
            int sequence,
            int lineNumber,
            UUID requestId,
            CompareStrategyRequest sourceRequest,
            BatchItemErrorCode code,
            String message
    ) {
        return new BatchItemResult(
                sequence,
                lineNumber,
                requestId,
                BatchItemStatus.FAILED,
                null,
                new BatchItemError(code, message),
                sourceRequest
        );
    }
}

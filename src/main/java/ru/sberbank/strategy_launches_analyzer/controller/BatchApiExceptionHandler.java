package ru.sberbank.strategy_launches_analyzer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.sberbank.strategy_launches_analyzer.dto.api.ErrorResponse;
import ru.sberbank.strategy_launches_analyzer.exceptions.BatchRequestException;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = BatchStrategyComparisonController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class BatchApiExceptionHandler {
    @ExceptionHandler(BatchRequestException.class)
    public ResponseEntity<ErrorResponse> handleBatchRequest(BatchRequestException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        log.warn("Batch comparison rejected: status={}, message={}", status.value(), ex.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (ex.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, ex.getRetryAfterSeconds().toString());
        }
        return response.body(new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage()
        ));
    }
}

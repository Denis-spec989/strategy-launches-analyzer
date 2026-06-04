package com.github.denisspec989.strategy_launches_analyzer.api;

import com.github.denisspec989.strategy_launches_analyzer.application.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        LOGGER.info("LGD_DIGITAL comparison request rejected: status={}, message={}",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        LOGGER.info("LGD_DIGITAL comparison request rejected: status={}, message={}",
                HttpStatus.BAD_REQUEST.value(),
                "Request body must be valid JSON.");
        return error(HttpStatus.BAD_REQUEST, "Request body must be valid JSON.");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message
                ));
    }
}

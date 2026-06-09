package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.github.denisspec989.strategy_launches_analyzer.dto.api.ErrorResponse;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        log.info("LGD_DIGITAL comparison request rejected: status={}, message={}",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.info("LGD_DIGITAL comparison request rejected: status={}, message={}",
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

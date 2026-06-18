package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.ErrorResponse;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentUnavailableException;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Arrays;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        log.info("Comparison request rejected: status={}, message={}",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(ApiExceptionHandler::fieldErrorMessage)
                .orElse("Request body is invalid.");
        log.info("Comparison request rejected: status={}, message={}",
                HttpStatus.BAD_REQUEST.value(),
                message);
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        String message = requestBodyMessage(ex);
        log.info("Comparison request rejected: status={}, message={}",
                HttpStatus.BAD_REQUEST.value(),
                message);
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(AgentAnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAgentAnalysisException(AgentAnalysisException ex) {
        String message = "Agent analysis failed. Check LLM configuration and availability.";
        log.error("Comparison request failed: status={}, message={}, causeType={}",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                message,
                causeType(ex));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    @ExceptionHandler(AgentUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAgentUnavailable(AgentUnavailableException ex) {
        String message = "Agent analysis is temporarily unavailable. Retry later.";
        log.warn("Comparison request rejected: status={}, message={}",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                message);
        return error(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // Стандартные Spring MVC исключения (404/405/415 и т.п.) реализуют ErrorResponse —
        // сохраняем их статус, чтобы catch-all не превращал их в 500.
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            String message = status.is5xxServerError() ? "Internal error." : status.getReasonPhrase() + ".";
            log.info("Comparison request not handled: status={}, message={}", status.value(), message);
            return error(status, message);
        }
        log.error("Comparison request failed unexpectedly: status={}, errorType={}",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getClass().getSimpleName(),
                ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error.");
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

    private static String fieldErrorMessage(FieldError fieldError) {
        if (fieldError.getDefaultMessage() == null || fieldError.getDefaultMessage().isBlank()) {
            return fieldError.getField() + " is invalid.";
        }
        return fieldError.getDefaultMessage();
    }

    private static String requestBodyMessage(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormat
                && invalidFormat.getTargetType() == StrategyName.class) {
            return "strategy must be one of: " + supportedStrategies() + ".";
        }
        return "Request body must be valid JSON.";
    }

    private static String supportedStrategies() {
        return String.join(", ", Arrays.stream(StrategyName.values())
                .map(StrategyName::name)
                .toList());
    }

    private static String causeType(Throwable ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        return cause.getClass().getSimpleName();
    }

}

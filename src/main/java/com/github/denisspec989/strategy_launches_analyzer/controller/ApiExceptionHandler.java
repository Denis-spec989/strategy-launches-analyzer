package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.ErrorResponse;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
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
    public ResponseEntity<ErrorResponse> handleAgentAnalysisFailure(AgentAnalysisException ex) {
        log.warn("Comparison request failed during agent analysis: status={}, errorType={}, message={}",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getClass().getSimpleName(),
                safeMessage(ex));
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "LLM analysis failed.");
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

    private static String safeMessage(RuntimeException ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "not-provided";
        }
        String message = ex.getMessage()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        return message.length() <= 128 ? message : message.substring(0, 128);
    }
}

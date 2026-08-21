package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.ErrorResponse;
import com.github.denisspec989.strategy_launches_analyzer.dto.strategy.StrategyName;
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
import java.util.UUID;
import java.util.stream.Collectors;

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
                .map(ApiExceptionHandler::fieldErrorMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request body is invalid.";
        }
        log.warn("Comparison request rejected: status={}, message={}, validationErrors={}",
                HttpStatus.BAD_REQUEST.value(),
                message,
                ex.getBindingResult().getAllErrors(),
                ex);
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        String message = requestBodyMessage(ex);
        log.warn("Comparison request rejected: status={}, message={}, errorType={}",
                HttpStatus.BAD_REQUEST.value(),
                message,
                ex.getMostSpecificCause().getClass().getSimpleName(),
                ex);
        return error(HttpStatus.BAD_REQUEST, message);
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
        InvalidFormatException invalidFormat = findCause(ex, InvalidFormatException.class);
        if (invalidFormat != null) {
            String path = jsonPath(invalidFormat);
            if (invalidFormat.getTargetType() == StrategyName.class) {
                return "strategy must be one of: " + supportedStrategies() + ".";
            }
            if (invalidFormat.getTargetType() == UUID.class) {
                return field(path, "requestId")
                        + " must be a canonical lowercase UUID, for example "
                        + "123e4567-e89b-12d3-a456-426614174000.";
            }
            if (invalidFormat.getTargetType() == Instant.class) {
                return field(path, "date-time")
                        + " must be an ISO-8601 date-time with timezone, for example "
                        + "2026-08-21T10:43:25Z.";
            }
            return field(path, "request field") + " has an invalid value for "
                    + invalidFormat.getTargetType().getSimpleName() + ".";
        }
        JsonMappingException mappingException = findCause(ex, JsonMappingException.class);
        if (mappingException != null) {
            return "Invalid value at " + field(jsonPath(mappingException), "request body")
                    + ": " + sentence(mappingException.getOriginalMessage());
        }
        JsonProcessingException processingException = findCause(ex, JsonProcessingException.class);
        if (processingException != null) {
            JsonLocation location = processingException.getLocation();
            String position = location == null
                    ? ""
                    : " at line %d, column %d".formatted(location.getLineNr(), location.getColumnNr());
            return "Malformed JSON" + position + ": "
                    + sentence(processingException.getOriginalMessage());
        }
        return "Request body cannot be read: " + sentence(cause.getMessage());
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String jsonPath(JsonMappingException exception) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : exception.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String field(String path, String fallback) {
        return path == null || path.isBlank() ? fallback : path;
    }

    private static String sentence(String value) {
        if (value == null || value.isBlank()) {
            return "unknown parsing error.";
        }
        String singleLine = value.replaceAll("\\s+", " ").strip();
        return singleLine.matches(".*[.!?]$") ? singleLine : singleLine + ".";
    }

    private static String supportedStrategies() {
        return String.join(", ", Arrays.stream(StrategyName.values())
                .map(StrategyName::name)
                .toList());
    }

}

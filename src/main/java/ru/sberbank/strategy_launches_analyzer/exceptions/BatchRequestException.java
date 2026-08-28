package ru.sberbank.strategy_launches_analyzer.exceptions;

public class BatchRequestException extends RuntimeException {
    private final int status;
    private final Integer retryAfterSeconds;

    public BatchRequestException(int status, String message) {
        this(status, message, null, null);
    }

    public BatchRequestException(int status, String message, Throwable cause) {
        this(status, message, null, cause);
    }

    public BatchRequestException(int status, String message, Integer retryAfterSeconds) {
        this(status, message, retryAfterSeconds, null);
    }

    private BatchRequestException(int status, String message, Integer retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getStatus() {
        return status;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

package ru.sberbank.strategy_launches_analyzer.service.batch;

public record BatchInputLine(
        int sequence,
        int lineNumber,
        byte[] content
) {
}

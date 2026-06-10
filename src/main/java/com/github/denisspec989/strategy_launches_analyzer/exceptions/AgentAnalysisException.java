package com.github.denisspec989.strategy_launches_analyzer.exceptions;

public class AgentAnalysisException extends RuntimeException {
    public AgentAnalysisException(String message) {
        super(message);
    }

    public AgentAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}

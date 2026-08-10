package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

public enum GuardrailCorrectionType {
    OVERALL_SEVERITY_CHANGED,
    DIFF_SEVERITY_ESCALATED,
    HARD_CRITICAL_EXPLANATION_ADDED,
    NON_CRITICAL_EXPLANATION_ADDED,
    CRITICAL_CONTRACT_RISK_APPENDED
}

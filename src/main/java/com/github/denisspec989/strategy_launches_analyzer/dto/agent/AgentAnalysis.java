package com.github.denisspec989.strategy_launches_analyzer.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentAnalysis(
        AgentAnalysisStatus status,
        Severity overallSeverity,
        String summary,
        String businessImpact,
        String technicalRisks,
        List<String> recommendations,
        List<DiffExplanation> diffExplanations,
        TokenUsage tokenUsage,
        String errorMessage
) {
    public static AgentAnalysis failed(String errorMessage) {
        return failed(errorMessage, Severity.WARNING);
    }

    public static AgentAnalysis failed(String errorMessage, Severity deterministicSeverity) {
        return failed(errorMessage, deterministicSeverity, TokenUsage.zero());
    }

    public static AgentAnalysis failed(
            String errorMessage,
            Severity deterministicSeverity,
            TokenUsage tokenUsage
    ) {
        Severity failureSeverity = max(deterministicSeverity, Severity.WARNING);
        return new AgentAnalysis(
                AgentAnalysisStatus.FAILED,
                failureSeverity,
                "LLM-анализ недоступен. Детерминированные отличия и результаты проверки контракта возвращены.",
                "Бизнес-влияние автоматически не оценено.",
                "Используйте детерминированные отличия и результаты проверки контракта для ручной оценки рисков.",
                List.of("Проверьте детерминированные результаты вручную и повторите запрос позднее."),
                List.of(),
                tokenUsage == null ? TokenUsage.zero() : tokenUsage,
                errorMessage
        );
    }

    private static Severity max(Severity left, Severity right) {
        Severity safeLeft = left == null ? Severity.WARNING : left;
        Severity safeRight = right == null ? Severity.WARNING : right;
        return safeLeft.ordinal() >= safeRight.ordinal() ? safeLeft : safeRight;
    }
}

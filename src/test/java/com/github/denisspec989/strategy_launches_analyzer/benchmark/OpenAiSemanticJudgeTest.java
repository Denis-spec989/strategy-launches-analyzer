package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiSemanticJudgeTest {
    @Test
    void candidateIdentityAndTokenMetadataAreNotIncludedInJudgePrompt() {
        AgentAnalysis analysis = new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                Severity.WARNING,
                "Обнаружено изменение.",
                "Нужно проверить влияние.",
                "Критических рисков нет.",
                List.of("Проверить результат."),
                List.of(),
                new TokenUsage(100, 20, 120, 10L, 0L, "secret-candidate-model"),
                null
        );
        OpenAiSemanticJudge judge = new OpenAiSemanticJudge(
                null, new ObjectMapper().findAndRegisterModules(), "fixed-judge-model"
        );

        String prompt = judge.userPrompt(
                null,
                analysis,
                new SemanticExpectations(List.of("Факт"), List.of(), List.of("Действие"))
        );

        assertThat(prompt).contains("Обнаружено изменение.");
        assertThat(prompt).doesNotContain("secret-candidate-model", "tokenUsage", "inputTokens");
    }
}

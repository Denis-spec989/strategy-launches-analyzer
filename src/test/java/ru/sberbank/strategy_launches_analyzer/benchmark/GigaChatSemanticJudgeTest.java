package ru.sberbank.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import ru.sberbank.strategy_launches_analyzer.dto.common.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GigaChatSemanticJudgeTest {
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
                null,
                null
        );
        GigaChatSemanticJudge judge = new GigaChatSemanticJudge(
                null, new ObjectMapper().findAndRegisterModules(), "fixed-judge-model"
        );

        String prompt = judge.userPrompt(
                null,
                analysis,
                new SemanticExpectations(List.of("Факт"), List.of(), List.of("Действие"))
        );

        assertThat(prompt).contains("Обнаружено изменение.");
        assertThat(prompt).doesNotContain("tokenUsage", "inputTokens", "outputTokens");
    }
}

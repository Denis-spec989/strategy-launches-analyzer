package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import ru.sberbank.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@RequiredArgsConstructor
public class GigaChatAgentClient implements AgentModelClient {
    private final GigaChatStructuredCompletionClient completionClient;
    private final AgentPromptBuilder promptBuilder;

    @Override
    public AgentModelCallResult call(AgentAnalysisInput input, AgentCallOptions options) {
        long startedAt = System.nanoTime();
        String userPrompt = options.repairContext() == null
                ? promptBuilder.buildUserPrompt(input)
                : promptBuilder.buildRepairPrompt(input, options.repairContext());
        GigaChatStructuredCompletionResult<StructuredAgentAnalysis> result = completionClient.complete(
                options.model(),
                AgentPromptBuilder.SYSTEM_PROMPT,
                userPrompt,
                StructuredAgentAnalysis.class
        );
        return new AgentModelCallResult(
                result.entity(),
                result.tokenUsage(),
                options.model(),
                result.actualModel(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        );
    }
}

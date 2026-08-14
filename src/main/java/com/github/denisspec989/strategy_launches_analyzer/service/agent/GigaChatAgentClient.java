package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@RequiredArgsConstructor
public class GigaChatAgentClient implements AgentModelClient {
    private final GigaChatStructuredCompletionClient completionClient;
    private final AgentPromptBuilder promptBuilder;

    @Override
    public AgentModelCallResult call(AgentAnalysisInput input, AgentCallOptions options) {
        long startedAt = System.nanoTime();
        GigaChatStructuredCompletionResult<StructuredAgentAnalysis> result = completionClient.complete(
                options.model(),
                AgentPromptBuilder.SYSTEM_PROMPT,
                promptBuilder.buildUserPrompt(input),
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

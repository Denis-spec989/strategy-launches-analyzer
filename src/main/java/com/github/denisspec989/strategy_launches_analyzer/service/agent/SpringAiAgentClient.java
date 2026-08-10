package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;

@RequiredArgsConstructor
public class SpringAiAgentClient implements AgentModelClient {
    private final ChatClient chatClient;
    private final AgentPromptBuilder promptBuilder;

    @Override
    public AgentModelCallResult call(AgentAnalysisInput input, AgentCallOptions options) {
        long startedAt = System.nanoTime();
        String userPrompt = promptBuilder.buildUserPrompt(input);
        OpenAiChatOptions.Builder requestOptions = requestOptions(options.model());
        ResponseEntity<ChatResponse, StructuredAgentAnalysis> responseEntity = chatClient.prompt()
                .options(requestOptions)
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .system(AgentPromptBuilder.SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .responseEntity(StructuredAgentAnalysis.class);
        TokenUsage tokenUsage = tokenUsage(responseEntity.getResponse());
        return new AgentModelCallResult(
                responseEntity.getEntity(),
                tokenUsage,
                options.model(),
                tokenUsage.model(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        );
    }

    static OpenAiChatOptions.Builder requestOptions(String model) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        options.model(model);
        return options;
    }

    private static TokenUsage tokenUsage(ChatResponse response) {
        if (response == null) {
            return TokenUsage.zero();
        }
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata == null) {
            return TokenUsage.zero();
        }
        Usage usage = metadata.getUsage();
        if (usage == null) {
            return new TokenUsage(null, null, null, null, null, metadata.getModel());
        }
        return new TokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens(),
                usage.getCacheReadInputTokens(),
                usage.getCacheWriteInputTokens(),
                metadata.getModel()
        );
    }
}

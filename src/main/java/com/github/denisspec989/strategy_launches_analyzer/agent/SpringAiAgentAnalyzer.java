package com.github.denisspec989.strategy_launches_analyzer.agent;

import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.domain.Severity;
import com.github.denisspec989.strategy_launches_analyzer.domain.TokenUsage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

public class SpringAiAgentAnalyzer implements AgentAnalyzer {
    private final ChatClient chatClient;
    private final AgentPromptBuilder promptBuilder;

    public SpringAiAgentAnalyzer(ChatClient.Builder chatClientBuilder, AgentPromptBuilder promptBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.promptBuilder = promptBuilder;
    }

    @Override
    public AgentAnalysis analyze(AgentAnalysisInput input) {
        try {
            ResponseEntity<ChatResponse, StructuredAgentAnalysis> responseEntity = chatClient.prompt()
                    .system(AgentPromptBuilder.SYSTEM_PROMPT)
                    .user(promptBuilder.buildUserPrompt(input))
                    .call()
                    .responseEntity(StructuredAgentAnalysis.class);
            return toDomain(responseEntity.getEntity(), tokenUsage(responseEntity.getResponse()));
        } catch (RuntimeException ex) {
            return AgentAnalysis.failed(ex.getMessage());
        }
    }

    private static AgentAnalysis toDomain(StructuredAgentAnalysis response, TokenUsage tokenUsage) {
        if (response == null) {
            return AgentAnalysis.failed("Model returned an empty structured response.");
        }
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                response.overallSeverity() == null ? Severity.WARNING : response.overallSeverity(),
                nullToEmpty(response.summary()),
                nullToEmpty(response.businessImpact()),
                nullToEmpty(response.technicalRisks()),
                response.recommendations() == null ? List.of() : response.recommendations(),
                response.diffExplanations() == null ? List.of() : response.diffExplanations(),
                tokenUsage,
                null
        );
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record StructuredAgentAnalysis(
            Severity overallSeverity,
            String summary,
            String businessImpact,
            String technicalRisks,
            List<String> recommendations,
            List<DiffExplanation> diffExplanations
    ) {
    }
}

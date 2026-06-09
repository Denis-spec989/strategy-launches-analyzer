package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffType;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class SpringAiAgentAnalyzer implements AgentAnalyzer {
    private final ChatClient chatClient;
    private final AgentPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final String configuredModel;

    @Override
    public AgentAnalysis analyze(AgentAnalysisInput input) {
        String requestId = requestId(input);
        String userPrompt = promptBuilder.buildUserPrompt(input);
        log.info("LLM analysis request started: requestId={}, strategyName={}, configuredModel={}, diffCount={}, contractIssueCount={}",
                requestId,
                input.strategyName(),
                configuredModel,
                input.diffs().size(),
                input.contractValidation().size());
        log.info("LLM system prompt: requestId={}, systemPrompt={}", requestId, AgentPromptBuilder.SYSTEM_PROMPT);
        log.info("LLM user prompt: requestId={}, userPrompt={}", requestId, userPrompt);
        try {
            ResponseEntity<ChatResponse, StructuredAgentAnalysis> responseEntity = chatClient.prompt()
                    .system(AgentPromptBuilder.SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .responseEntity(StructuredAgentAnalysis.class);
            ChatResponse chatResponse = responseEntity.getResponse();
            StructuredAgentAnalysis structuredResponse = responseEntity.getEntity();
            TokenUsage tokenUsage = tokenUsage(chatResponse);
            log.info("LLM raw assistant response: requestId={}, response={}", requestId, rawAssistantText(chatResponse));
            log.info("LLM structured response: requestId={}, response={}", requestId, prettyJson(structuredResponse));
            log.info("LLM response metadata: requestId={}, tokenUsage={}", requestId, prettyJson(tokenUsage));
            AgentAnalysis analysis = toDomain(structuredResponse, input, tokenUsage);
            log.info("LLM analysis mapped to domain: requestId={}, analysis={}", requestId, prettyJson(analysis));
            return analysis;
        } catch (RuntimeException ex) {
            log.info("LLM analysis request failed: requestId={}, configuredModel={}, error={}",
                    requestId,
                    configuredModel,
                    ex.toString());
            return AgentAnalysis.failed(ex.getMessage());
        }
    }

    static AgentAnalysis toDomain(StructuredAgentAnalysis response, AgentAnalysisInput input, TokenUsage tokenUsage) {
        if (response == null) {
            return AgentAnalysis.failed("Model returned an empty structured response.");
        }
        Severity overallSeverity = response.overallSeverity() == null ? Severity.WARNING : response.overallSeverity();
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                applyHardCriticalGuardrail(input, overallSeverity),
                nullToEmpty(response.summary()),
                nullToEmpty(response.businessImpact()),
                nullToEmpty(response.technicalRisks()),
                response.recommendations() == null ? List.of() : response.recommendations(),
                response.diffExplanations() == null ? List.of() : response.diffExplanations(),
                tokenUsage,
                null
        );
    }

    private static Severity applyHardCriticalGuardrail(AgentAnalysisInput input, Severity modelSeverity) {
        if (hasHardCriticalSignal(input)) {
            return Severity.CRITICAL;
        }
        return modelSeverity;
    }

    private static boolean hasHardCriticalSignal(AgentAnalysisInput input) {
        if (input == null) {
            return false;
        }
        boolean hasCriticalIssue = input.contractValidation().stream()
                .anyMatch(issue -> issue.severity() == Severity.CRITICAL);
        boolean hasCriticalDiff = input.diffs().stream()
                .anyMatch(SpringAiAgentAnalyzer::isHardCriticalDiff);
        return hasCriticalIssue || hasCriticalDiff;
    }

    private static boolean isHardCriticalDiff(DiffEntry diff) {
        return diff.type() == DiffType.TYPE_MISMATCH
                || diff.type() == DiffType.NULLABILITY_VIOLATION
                || diff.type() == DiffType.REQUIRED_FIELD_MISSING
                || diff.path().endsWith(".mode")
                || diff.path().endsWith(".type");
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

    private String prettyJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private static String rawAssistantText(ChatResponse response) {
        if (response == null) {
            return "not-provided";
        }
        Generation result = response.getResult();
        if (result == null) {
            return "not-provided";
        }
        AssistantMessage output = result.getOutput();
        if (output == null || output.getText() == null || output.getText().isBlank()) {
            return "not-provided";
        }
        return output.getText();
    }

    private static String requestId(AgentAnalysisInput input) {
        if (input.metadata() == null || input.metadata().requestId() == null || input.metadata().requestId().isBlank()) {
            return "not-provided";
        }
        return input.metadata().requestId();
    }

}

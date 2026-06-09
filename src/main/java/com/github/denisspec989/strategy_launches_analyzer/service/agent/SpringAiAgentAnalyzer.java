package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DeterministicSeverity;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class SpringAiAgentAnalyzer implements AgentAnalyzer {
    private static final int MAX_TEXT_LENGTH = 4_000;
    private static final int MAX_RECOMMENDATIONS = 10;
    private static final int MAX_RECOMMENDATION_LENGTH = 1_000;
    private static final int MAX_DIFF_EXPLANATION_LENGTH = 2_000;

    private final ChatClient chatClient;
    private final AgentPromptBuilder promptBuilder;
    private final String configuredModel;

    @Override
    public AgentAnalysis analyze(AgentAnalysisInput input) {
        String requestId = requestId(input);
        String userPrompt = promptBuilder.buildUserPrompt(input);
        long startedAtNanos = System.nanoTime();
        log.info(
                "LLM analysis request started: requestId={}, strategyName={}, configuredModel={}, diffCount={}, "
                        + "contractIssueCount={}, promptChars={}",
                requestId,
                input.strategyName(),
                configuredModel,
                input.diffs().size(),
                input.contractValidation().size(),
                userPrompt.length());
        try {
            ResponseEntity<ChatResponse, StructuredAgentAnalysis> responseEntity = chatClient.prompt()
                    .system(AgentPromptBuilder.SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .responseEntity(StructuredAgentAnalysis.class);
            ChatResponse chatResponse = responseEntity.getResponse();
            StructuredAgentAnalysis structuredResponse = responseEntity.getEntity();
            TokenUsage tokenUsage = tokenUsage(chatResponse);
            AgentAnalysis analysis = toDomain(structuredResponse, input, tokenUsage);
            log.info(
                    "LLM analysis request completed: requestId={}, configuredModel={}, responseModel={}, status={}, "
                            + "overallSeverity={}, inputTokens={}, outputTokens={}, totalTokens={}, durationMs={}",
                    requestId,
                    configuredModel,
                    logValue(tokenUsage.model()),
                    analysis.status(),
                    analysis.overallSeverity(),
                    tokenUsage.inputTokens(),
                    tokenUsage.outputTokens(),
                    tokenUsage.totalTokens(),
                    durationMs(startedAtNanos));
            return analysis;
        } catch (AgentAnalysisException ex) {
            log.warn(
                    "LLM structured output rejected: requestId={}, configuredModel={}, errorType={}, errorMessage={}, durationMs={}",
                    requestId,
                    configuredModel,
                    ex.getClass().getSimpleName(),
                    safeMessage(ex),
                    durationMs(startedAtNanos));
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("LLM analysis request failed: requestId={}, configuredModel={}, errorType={}, errorMessage={}, durationMs={}",
                    requestId,
                    configuredModel,
                    ex.getClass().getSimpleName(),
                    safeMessage(ex),
                    durationMs(startedAtNanos));
            throw new AgentAnalysisException("LLM analysis failed.", ex);
        }
    }

    static AgentAnalysis toDomain(StructuredAgentAnalysis response, AgentAnalysisInput input, TokenUsage tokenUsage) {
        validateStructuredResponse(response, input);
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                applyHardCriticalGuardrail(input, response.overallSeverity()),
                response.summary(),
                response.businessImpact(),
                response.technicalRisks(),
                response.recommendations(),
                response.diffExplanations(),
                tokenUsage,
                null
        );
    }

    private static Severity applyHardCriticalGuardrail(AgentAnalysisInput input, Severity modelSeverity) {
        Severity deterministicSeverity = DeterministicSeverity.from(input.diffs(), input.contractValidation());
        return modelSeverity.ordinal() >= deterministicSeverity.ordinal() ? modelSeverity : deterministicSeverity;
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

    private static String requestId(AgentAnalysisInput input) {
        if (input.metadata() == null || input.metadata().requestId() == null || input.metadata().requestId().isBlank()) {
            return "not-provided";
        }
        return logValue(input.metadata().requestId());
    }

    private static void validateStructuredResponse(StructuredAgentAnalysis response, AgentAnalysisInput input) {
        if (response == null) {
            throw new AgentAnalysisException("Model returned an empty structured response.");
        }
        if (response.overallSeverity() == null) {
            throw new AgentAnalysisException("Model response overallSeverity is required.");
        }
        requireText("summary", response.summary(), MAX_TEXT_LENGTH);
        requireText("businessImpact", response.businessImpact(), MAX_TEXT_LENGTH);
        requireText("technicalRisks", response.technicalRisks(), MAX_TEXT_LENGTH);
        validateRecommendations(response.recommendations());
        validateDiffExplanations(response.diffExplanations(), input);
    }

    private static void validateRecommendations(List<String> recommendations) {
        if (recommendations == null) {
            throw new AgentAnalysisException("Model response recommendations are required.");
        }
        if (recommendations.size() > MAX_RECOMMENDATIONS) {
            throw new AgentAnalysisException("Model response recommendations exceed the maximum size.");
        }
        for (String recommendation : recommendations) {
            requireText("recommendation", recommendation, MAX_RECOMMENDATION_LENGTH);
        }
    }

    private static void validateDiffExplanations(List<DiffExplanation> explanations, AgentAnalysisInput input) {
        if (explanations == null) {
            throw new AgentAnalysisException("Model response diffExplanations are required.");
        }
        if (explanations.size() > input.diffs().size()) {
            throw new AgentAnalysisException("Model response diffExplanations exceed deterministic diff count.");
        }

        Map<String, String> diffPathsById = new LinkedHashMap<>();
        for (DiffEntry diff : input.diffs()) {
            diffPathsById.put(diff.id(), diff.path());
        }

        Set<String> explainedDiffIds = new LinkedHashSet<>();
        for (DiffExplanation explanation : explanations) {
            if (explanation == null) {
                throw new AgentAnalysisException("Model response diffExplanation cannot be null.");
            }
            requireText("diffExplanation.diffId", explanation.diffId(), 128);
            requireText("diffExplanation.path", explanation.path(), 512);
            requireText("diffExplanation.explanation", explanation.explanation(), MAX_DIFF_EXPLANATION_LENGTH);
            if (explanation.severity() == null) {
                throw new AgentAnalysisException("Model response diffExplanation severity is required.");
            }

            String deterministicPath = diffPathsById.get(explanation.diffId());
            if (deterministicPath == null) {
                throw new AgentAnalysisException("Model response references an unknown diffId.");
            }
            if (!deterministicPath.equals(explanation.path())) {
                throw new AgentAnalysisException("Model response diffExplanation path does not match deterministic diff.");
            }
            if (!explainedDiffIds.add(explanation.diffId())) {
                throw new AgentAnalysisException("Model response contains duplicate diffExplanation entries.");
            }
        }

        List<String> missingHardCriticalDiffs = input.diffs().stream()
                .filter(DeterministicSeverity::isHardCriticalDiff)
                .map(DiffEntry::id)
                .filter(diffId -> !explainedDiffIds.contains(diffId))
                .toList();
        if (!missingHardCriticalDiffs.isEmpty()) {
            throw new AgentAnalysisException("Model response does not explain all hard-critical diffs.");
        }
    }

    private static void requireText(String fieldName, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new AgentAnalysisException("Model response " + fieldName + " is required.");
        }
        if (value.length() > maxLength) {
            throw new AgentAnalysisException("Model response " + fieldName + " exceeds the maximum length.");
        }
    }

    private static long durationMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static String safeMessage(RuntimeException ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "not-provided";
        }
        return logValue(ex.getMessage());
    }

    private static String logValue(Object value) {
        if (value == null) {
            return "not-provided";
        }
        String text = String.valueOf(value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        return text.length() <= 128 ? text : text.substring(0, 128);
    }

}

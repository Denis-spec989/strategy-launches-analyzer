package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DeterministicSeverityCalculator;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.DiffEntry;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class SpringAiAgentAnalyzer implements AgentAnalyzer {
    private static final int MAX_RECOMMENDATIONS = 10;

    private final ChatClient chatClient;
    private final AgentPromptBuilder promptBuilder;
    private final String configuredModel;

    @Override
    public AgentAnalysis analyze(AgentAnalysisInput input) {
        String requestId = requestId(input);
        long startedAt = System.nanoTime();
        log.info("LLM analysis request started: requestId={}, strategyName={}, configuredModel={}, "
                        + "diffCount={}, contractIssueCount={}",
                requestId,
                input.strategyName(),
                configuredModel,
                input.diffs().size(),
                input.contractValidation().size());
        try {
            String userPrompt = promptBuilder.buildUserPrompt(input);
            ResponseEntity<ChatResponse, StructuredAgentAnalysis> responseEntity = chatClient.prompt()
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .system(AgentPromptBuilder.SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .responseEntity(StructuredAgentAnalysis.class);
            ChatResponse chatResponse = responseEntity.getResponse();
            TokenUsage tokenUsage = tokenUsage(chatResponse);
            AgentAnalysis analysis = toDomain(responseEntity.getEntity(), input, tokenUsage);
            log.info("LLM analysis request completed: requestId={}, status={}, overallSeverity={}, "
                            + "recommendationCount={}, diffExplanationCount={}, inputTokens={}, outputTokens={}, "
                            + "totalTokens={}, model={}, durationMs={}",
                    requestId,
                    analysis.status(),
                    analysis.overallSeverity(),
                    analysis.recommendations().size(),
                    analysis.diffExplanations().size(),
                    tokenUsage.inputTokens(),
                    tokenUsage.outputTokens(),
                    tokenUsage.totalTokens(),
                    valueOrNotProvided(tokenUsage.model()),
                    elapsedMs(startedAt));
            return analysis;
        } catch (AgentAnalysisException ex) {
            log.error("LLM analysis request rejected: requestId={}, configuredModel={}, errorType={}, message={}",
                    requestId,
                    configuredModel,
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("LLM analysis request failed: requestId={}, configuredModel={}, errorType={}",
                    requestId,
                    configuredModel,
                    ex.getClass().getSimpleName());
            throw new AgentAnalysisException("LLM analysis request failed.", ex);
        }
    }

    static AgentAnalysis toDomain(StructuredAgentAnalysis response, AgentAnalysisInput input, TokenUsage tokenUsage) {
        validate(response, input);
        List<DiffExplanation> diffExplanations = withDeterministicDiffExplanations(
                input,
                response.diffExplanations() == null ? List.of() : response.diffExplanations()
        );
        Severity overallSeverity = escalateToExplanationSeverity(
                applyHardCriticalGuardrail(input, response.overallSeverity()),
                diffExplanations
        );
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                overallSeverity,
                response.summary().trim(),
                response.businessImpact().trim(),
                withCriticalContractIssueRisk(response.technicalRisks().trim(), input),
                copyTrimmed(response.recommendations()),
                diffExplanations,
                tokenUsage == null ? TokenUsage.zero() : tokenUsage,
                null
        );
    }

    private static void validate(StructuredAgentAnalysis response, AgentAnalysisInput input) {
        List<String> violations = new ArrayList<>();
        if (response == null) {
            violations.add("response is empty");
            throw invalidResponse(violations);
        }
        if (response.overallSeverity() == null) {
            violations.add("overallSeverity is null");
        }
        requireNonBlank(response.summary(), "summary", violations);
        requireNonBlank(response.businessImpact(), "businessImpact", violations);
        requireNonBlank(response.technicalRisks(), "technicalRisks", violations);
        validateRecommendations(response.recommendations(), violations);
        validateDiffExplanations(response.diffExplanations(), input, violations);
        if (!violations.isEmpty()) {
            throw invalidResponse(violations);
        }
    }

    private static void validateRecommendations(List<String> recommendations, List<String> violations) {
        if (recommendations == null) {
            return;
        }
        if (recommendations.size() > MAX_RECOMMENDATIONS) {
            violations.add("recommendations exceeds max size " + MAX_RECOMMENDATIONS);
        }
        for (int index = 0; index < recommendations.size(); index++) {
            if (isBlank(recommendations.get(index))) {
                violations.add("recommendations[" + index + "] is blank");
            }
        }
    }

    private static void validateDiffExplanations(
            List<DiffExplanation> explanations,
            AgentAnalysisInput input,
            List<String> violations
    ) {
        Map<String, String> expectedDiffs = expectedDiffs(input);
        List<DiffExplanation> safeExplanations = explanations == null ? List.of() : explanations;
        if (safeExplanations.size() > expectedDiffs.size()) {
            violations.add("diffExplanations exceeds deterministic diff count");
        }

        Set<String> coveredDiffIds = new LinkedHashSet<>();
        for (int index = 0; index < safeExplanations.size(); index++) {
            DiffExplanation explanation = safeExplanations.get(index);
            if (explanation == null) {
                violations.add("diffExplanations[" + index + "] is null");
                continue;
            }
            requireNonBlank(explanation.diffId(), "diffExplanations[" + index + "].diffId", violations);
            requireNonBlank(explanation.path(), "diffExplanations[" + index + "].path", violations);
            if (explanation.severity() == null) {
                violations.add("diffExplanations[" + index + "].severity is null");
            }
            requireNonBlank(explanation.explanation(), "diffExplanations[" + index + "].explanation", violations);

            String expectedPath = expectedDiffs.get(explanation.diffId());
            if (expectedPath == null) {
                violations.add("diffExplanations[" + index + "].diffId is not in deterministic diffs");
                continue;
            }
            if (!expectedPath.equals(explanation.path())) {
                violations.add("diffExplanations[" + index + "].path does not match deterministic diff");
            }
            if (!coveredDiffIds.add(explanation.diffId())) {
                violations.add("diffExplanations[" + index + "].diffId is duplicated");
            }
        }
    }

    private static List<DiffExplanation> withDeterministicDiffExplanations(
            AgentAnalysisInput input,
            List<DiffExplanation> modelExplanations
    ) {
        Map<String, DiffExplanation> byDiffId = new LinkedHashMap<>();
        Map<String, DiffEntry> hardCriticalDiffs = new LinkedHashMap<>();
        input.diffs().stream()
                .filter(DeterministicSeverityCalculator::isHardCriticalDiff)
                .forEach(diff -> hardCriticalDiffs.put(diff.id(), diff));
        modelExplanations.forEach(explanation -> byDiffId.put(
                explanation.diffId(),
                hardCriticalDiffs.containsKey(explanation.diffId())
                        ? withCriticalSeverity(explanation)
                        : explanation
        ));
        input.diffs().stream()
                .filter(DeterministicSeverityCalculator::isHardCriticalDiff)
                .filter(diff -> !byDiffId.containsKey(diff.id()))
                .forEach(diff -> byDiffId.put(diff.id(), deterministicHardCriticalExplanation(diff)));

        List<DiffEntry> uncoveredNonCriticalDiffs = expectedNonCriticalDiffs(input).stream()
                .filter(diff -> !byDiffId.containsKey(diff.id()))
                .toList();
        uncoveredNonCriticalDiffs.forEach(diff -> byDiffId.put(diff.id(), neutralNonCriticalExplanation(diff)));
        if (!uncoveredNonCriticalDiffs.isEmpty()) {
            log.warn("Filled {} non-critical diffExplanations omitted by the model with neutral stubs: diffIds={}",
                    uncoveredNonCriticalDiffs.size(),
                    uncoveredNonCriticalDiffs.stream().map(DiffEntry::id).limit(10).toList());
        }
        return List.copyOf(byDiffId.values());
    }

    private static DiffExplanation withCriticalSeverity(DiffExplanation explanation) {
        if (explanation.severity() == Severity.CRITICAL) {
            return explanation;
        }
        return new DiffExplanation(
                explanation.diffId(),
                explanation.path(),
                Severity.CRITICAL,
                explanation.explanation()
        );
    }

    private static DiffExplanation deterministicHardCriticalExplanation(DiffEntry diff) {
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.CRITICAL,
                "\u0414\u0435\u0442\u0435\u0440\u043c\u0438\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u0430\u044f "
                        + "\u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 \u043f\u043e\u043c\u0435\u0442\u0438\u043b\u0430 "
                        + "\u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0435 \u043a\u0430\u043a "
                        + "\u043a\u0440\u0438\u0442\u0438\u0447\u0435\u0441\u043a\u043e\u0435: "
                        + "\u0438\u0437\u043c\u0435\u043d\u0438\u043b\u0441\u044f "
                        + "\u0442\u0435\u0445\u043d\u0438\u0447\u0435\u0441\u043a\u0438\u0439, "
                        + "\u0442\u0438\u043f\u043e\u0432\u043e\u0439 \u0438\u043b\u0438 "
                        + "\u043e\u0431\u044f\u0437\u0430\u0442\u0435\u043b\u044c\u043d\u044b\u0439 "
                        + "contract-level \u043f\u0440\u0438\u0437\u043d\u0430\u043a."
        );
    }

    private static DiffExplanation neutralNonCriticalExplanation(DiffEntry diff) {
        return new DiffExplanation(
                diff.id(),
                diff.path(),
                Severity.WARNING,
                "\u041d\u0435\u043a\u0440\u0438\u0442\u0438\u0447\u043d\u043e\u0435 \u0438\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0435; "
                        + "\u043c\u043e\u0434\u0435\u043b\u044c \u043d\u0435 \u043f\u0440\u0435\u0434\u043e\u0441\u0442\u0430\u0432\u0438\u043b\u0430 "
                        + "\u043e\u0442\u0434\u0435\u043b\u044c\u043d\u043e\u0435 \u043e\u0431\u044a\u044f\u0441\u043d\u0435\u043d\u0438\u0435."
        );
    }

    private static String withCriticalContractIssueRisk(String technicalRisks, AgentAnalysisInput input) {
        long criticalIssueCount = input.contractValidation().stream()
                .filter(issue -> issue.severity() == Severity.CRITICAL)
                .count();
        if (criticalIssueCount == 0) {
            return technicalRisks;
        }
        return technicalRisks + " \u0414\u0435\u0442\u0435\u0440\u043c\u0438\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u0430\u044f "
                + "contract validation \u043d\u0430\u0448\u043b\u0430 "
                + "\u043a\u0440\u0438\u0442\u0438\u0447\u0435\u0441\u043a\u0438\u0435 "
                + "\u043d\u0430\u0440\u0443\u0448\u0435\u043d\u0438\u044f: "
                + criticalIssueCount + ". \u0418\u0445 \u043d\u0443\u0436\u043d\u043e "
                + "\u0438\u0441\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u0438\u043b\u0438 "
                + "\u044f\u0432\u043d\u043e \u0441\u043e\u0433\u043b\u0430\u0441\u043e\u0432\u0430\u0442\u044c "
                + "\u0434\u043e promotion.";
    }

    private static Severity applyHardCriticalGuardrail(AgentAnalysisInput input, Severity modelSeverity) {
        if (DeterministicSeverityCalculator.hasCriticalSignal(input.diffs(), input.contractValidation())) {
            return Severity.CRITICAL;
        }
        return modelSeverity;
    }

    private static Severity escalateToExplanationSeverity(Severity base, List<DiffExplanation> diffExplanations) {
        Severity result = base;
        for (DiffExplanation explanation : diffExplanations) {
            if (explanation != null) {
                result = maxSeverity(result, explanation.severity());
            }
        }
        return result;
    }

    private static Severity maxSeverity(Severity left, Severity right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private static Map<String, String> expectedDiffs(AgentAnalysisInput input) {
        Map<String, String> expectedDiffs = new LinkedHashMap<>();
        input.diffs().forEach(diff -> expectedDiffs.put(diff.id(), diff.path()));
        return expectedDiffs;
    }

    private static List<DiffEntry> expectedNonCriticalDiffs(AgentAnalysisInput input) {
        return input.diffs().stream()
                .filter(diff -> !DeterministicSeverityCalculator.isHardCriticalDiff(diff))
                .toList();
    }

    private static AgentAnalysisException invalidResponse(List<String> violations) {
        return new AgentAnalysisException("Invalid structured LLM response: " + String.join("; ", violations) + ".");
    }

    private static void requireNonBlank(String value, String fieldName, List<String> violations) {
        if (isBlank(value)) {
            violations.add(fieldName + " is blank");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> copyTrimmed(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(String::trim)
                .toList();
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
        return input.metadata().requestId();
    }

    private static Object valueOrNotProvided(Object value) {
        return value == null ? "not-provided" : value;
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}

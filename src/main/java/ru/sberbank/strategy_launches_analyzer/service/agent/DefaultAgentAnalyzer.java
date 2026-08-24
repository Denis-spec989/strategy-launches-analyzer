package ru.sberbank.strategy_launches_analyzer.service.agent;

import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentFallbackReason;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import ru.sberbank.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import ru.sberbank.strategy_launches_analyzer.dto.agent.TokenUsage;
import ru.sberbank.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import ru.sberbank.strategy_launches_analyzer.exceptions.AgentExecutionFailureException;
import ru.sberbank.strategy_launches_analyzer.exceptions.RepairableAgentResponseException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultAgentAnalyzer implements AgentAnalyzer {
    private final AgentModelClient modelClient;
    private final AgentAnalysisPostProcessor postProcessor;
    private final String configuredModel;
    private final boolean repairEnabled;
    private final AgentMetrics metrics;

    public DefaultAgentAnalyzer(
            AgentModelClient modelClient,
            AgentAnalysisPostProcessor postProcessor,
            String configuredModel
    ) {
        this(modelClient, postProcessor, configuredModel, true, AgentMetrics.noop());
    }

    public DefaultAgentAnalyzer(
            AgentModelClient modelClient,
            AgentAnalysisPostProcessor postProcessor,
            String configuredModel,
            boolean repairEnabled,
            AgentMetrics metrics
    ) {
        this.modelClient = modelClient;
        this.postProcessor = postProcessor;
        this.configuredModel = configuredModel;
        this.repairEnabled = repairEnabled;
        this.metrics = metrics == null ? AgentMetrics.noop() : metrics;
    }

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
        AgentModelCallResult callResult = null;
        try {
            callResult = modelClient.call(input, new AgentCallOptions(configuredModel));
            try {
                AgentPostProcessingResult processed = postProcessor.process(
                        callResult.rawAnalysis(),
                        input,
                        callResult.tokenUsage()
                );
                recordCompleted(input, processed, callResult, startedAt, AgentMetrics.AnalysisOutcome.COMPLETED);
                return processed.analysis();
            } catch (RepairableAgentResponseException ex) {
                return repair(input, requestId, ex, startedAt);
            }
        } catch (RepairableAgentResponseException ex) {
            return repair(input, requestId, ex, startedAt);
        } catch (AgentExecutionFailureException ex) {
            throw ex;
        } catch (AgentAnalysisException ex) {
            log.error("LLM analysis request rejected: requestId={}, configuredModel={}, errorType={}, message={}",
                    requestId, configuredModel, ex.getClass().getSimpleName(), ex.getMessage());
            throw failure(
                    "LLM analysis request failed internally.",
                    ex,
                    AgentFallbackReason.INTERNAL,
                    callResult == null ? TokenUsage.zero() : callResult.tokenUsage()
            );
        } catch (RuntimeException ex) {
            log.error("LLM analysis request failed: requestId={}, configuredModel={}, errorType={}, message={}",
                    requestId, configuredModel, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            AgentFallbackReason reason = callResult == null
                    ? AgentFallbackReason.TRANSPORT
                    : AgentFallbackReason.INTERNAL;
            throw failure(
                    callResult == null ? "LLM provider request failed." : "LLM analysis processing failed.",
                    ex,
                    reason,
                    callResult == null ? TokenUsage.zero() : callResult.tokenUsage()
            );
        }
    }

    private AgentAnalysis repair(
            AgentAnalysisInput input,
            String requestId,
            RepairableAgentResponseException firstFailure,
            long startedAt
    ) {
        metrics.recordValidationFailure(input.strategyName(), firstFailure.reason());
        if (!repairEnabled) {
            throw failure(
                    "LLM response is invalid and repair is disabled.",
                    firstFailure,
                    AgentFallbackReason.RESPONSE_INVALID,
                    firstFailure.tokenUsage()
            );
        }

        log.warn("LLM repair attempt started: requestId={}, configuredModel={}, reason={}, violationCount={}",
                requestId, configuredModel, firstFailure.reason(), firstFailure.violations().size());
        AgentModelCallResult repairCall = null;
        try {
            repairCall = modelClient.call(
                    input,
                    new AgentCallOptions(configuredModel, firstFailure.repairContext())
            );
            TokenUsage totalUsage = TokenUsage.sum(firstFailure.tokenUsage(), repairCall.tokenUsage());
            AgentPostProcessingResult processed = postProcessor.process(
                    repairCall.rawAnalysis(),
                    input,
                    totalUsage
            );
            metrics.recordRepair(input.strategyName(), firstFailure.reason(), true);
            recordCompleted(input, processed, repairCall, startedAt, AgentMetrics.AnalysisOutcome.REPAIRED);
            log.info("LLM repair attempt completed: requestId={}, configuredModel={}, reason={}",
                    requestId, configuredModel, firstFailure.reason());
            return processed.analysis();
        } catch (RepairableAgentResponseException ex) {
            metrics.recordValidationFailure(input.strategyName(), ex.reason());
            metrics.recordRepair(input.strategyName(), firstFailure.reason(), false);
            TokenUsage totalUsage = repairCall == null
                    ? TokenUsage.sum(firstFailure.tokenUsage(), ex.tokenUsage())
                    : TokenUsage.sum(firstFailure.tokenUsage(), repairCall.tokenUsage());
            log.error("LLM repair attempt rejected: requestId={}, configuredModel={}, firstReason={}, "
                            + "repairReason={}, violationCount={}",
                    requestId, configuredModel, firstFailure.reason(), ex.reason(), ex.violations().size());
            throw failure(
                    "LLM repair attempt did not produce a valid response.",
                    ex,
                    AgentFallbackReason.REPAIR_EXHAUSTED,
                    totalUsage
            );
        } catch (RuntimeException ex) {
            metrics.recordRepair(input.strategyName(), firstFailure.reason(), false);
            TokenUsage totalUsage = TokenUsage.sum(
                    firstFailure.tokenUsage(),
                    repairCall == null ? null : repairCall.tokenUsage()
            );
            log.error("LLM repair attempt failed: requestId={}, configuredModel={}, firstReason={}, errorType={}",
                    requestId, configuredModel, firstFailure.reason(), ex.getClass().getSimpleName(), ex);
            throw failure(
                    "LLM repair attempt failed.",
                    ex,
                    AgentFallbackReason.REPAIR_EXHAUSTED,
                    totalUsage
            );
        }
    }

    private void recordCompleted(
            AgentAnalysisInput input,
            AgentPostProcessingResult processed,
            AgentModelCallResult callResult,
            long startedAt,
            AgentMetrics.AnalysisOutcome outcome
    ) {
        AgentAnalysis analysis = processed.analysis();
        TokenUsage usage = processed.tokenUsage();
        long durationNanos = System.nanoTime() - startedAt;
        metrics.recordAnalysis(input.strategyName(), outcome, durationNanos, processed.corrections(), usage);
        log.info("LLM analysis request completed: requestId={}, status={}, outcome={}, overallSeverity={}, "
                        + "recommendationCount={}, diffExplanationCount={}, guardrailCorrectionCount={}, "
                        + "inputTokens={}, outputTokens={}, totalTokens={}, model={}, durationMs={}",
                requestId(input),
                analysis.status(),
                outcome,
                analysis.overallSeverity(),
                analysis.recommendations().size(),
                analysis.diffExplanations().size(),
                processed.corrections().size(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                valueOrNotProvided(callResult.actualModel()),
                java.time.Duration.ofNanos(durationNanos).toMillis());
    }

    private static AgentExecutionFailureException failure(
            String message,
            Throwable cause,
            AgentFallbackReason reason,
            TokenUsage tokenUsage
    ) {
        return new AgentExecutionFailureException(message, cause, reason, tokenUsage);
    }

    private static String requestId(AgentAnalysisInput input) {
        if (input.metadata() == null || input.metadata().requestId() == null) {
            return "not-provided";
        }
        return input.metadata().requestId().toString();
    }

    private static Object valueOrNotProvided(Object value) {
        return value == null ? "not-provided" : value;
    }
}

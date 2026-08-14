package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.exceptions.AgentAnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class DefaultAgentAnalyzer implements AgentAnalyzer {
    private final AgentModelClient modelClient;
    private final AgentAnalysisPostProcessor postProcessor;
    private final String configuredModel;

    @Override
    public AgentAnalysis analyze(AgentAnalysisInput input) {
        String requestId = requestId(input);
        log.info("LLM analysis request started: requestId={}, strategyName={}, configuredModel={}, "
                        + "diffCount={}, contractIssueCount={}",
                requestId,
                input.strategyName(),
                configuredModel,
                input.diffs().size(),
                input.contractValidation().size());
        try {
            AgentModelCallResult callResult = modelClient.call(input, new AgentCallOptions(configuredModel));
            AgentPostProcessingResult processed = postProcessor.process(
                    callResult.rawAnalysis(),
                    input,
                    callResult.tokenUsage()
            );
            AgentAnalysis analysis = processed.analysis();
            TokenUsage usage = callResult.tokenUsage() == null ? TokenUsage.zero() : callResult.tokenUsage();
            log.info("LLM analysis request completed: requestId={}, status={}, overallSeverity={}, "
                            + "recommendationCount={}, diffExplanationCount={}, guardrailCorrectionCount={}, "
                            + "inputTokens={}, outputTokens={}, totalTokens={}, model={}, durationMs={}",
                    requestId,
                    analysis.status(),
                    analysis.overallSeverity(),
                    analysis.recommendations().size(),
                    analysis.diffExplanations().size(),
                    processed.corrections().size(),
                    usage.inputTokens(),
                    usage.outputTokens(),
                    usage.totalTokens(),
                    valueOrNotProvided(callResult.actualModel()),
                    callResult.durationMs());
            return analysis;
        } catch (AgentAnalysisException ex) {
            log.error("LLM analysis request rejected: requestId={}, configuredModel={}, errorType={}, message={}",
                    requestId, configuredModel, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("LLM analysis request failed: requestId={}, configuredModel={}, errorType={}, message={}",
                    requestId, configuredModel, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw new AgentAnalysisException("LLM analysis request failed.", ex);
        }
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
}

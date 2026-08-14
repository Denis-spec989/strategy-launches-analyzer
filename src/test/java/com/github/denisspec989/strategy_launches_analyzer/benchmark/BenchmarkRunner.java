package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentCallOptions;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentModelCallResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentAnalysisPostProcessor;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.AgentModelClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class BenchmarkRunner {
    private static final int EXPECTED_CASE_COUNT = 19;

    private final ObjectMapper objectMapper;
    private final AgentModelClient modelClient;
    private final AgentAnalysisPostProcessor postProcessor;
    private final SemanticJudge semanticJudge;
    private final BenchmarkInputFactory inputFactory;
    private final DeterministicAgentGrader deterministicGrader = new DeterministicAgentGrader();
    private final BenchmarkAggregator aggregator = new BenchmarkAggregator();

    BenchmarkRunner(
            ObjectMapper objectMapper,
            AgentModelClient modelClient,
            AgentAnalysisPostProcessor postProcessor,
            SemanticJudge semanticJudge,
            BenchmarkInputFactory inputFactory
    ) {
        this.objectMapper = objectMapper;
        this.modelClient = modelClient;
        this.postProcessor = postProcessor;
        this.semanticJudge = semanticJudge;
        this.inputFactory = inputFactory;
    }

    BenchmarkSummary run(BenchmarkConfiguration configuration) {
        List<BenchmarkCase> cases = new BenchmarkCaseLoader(objectMapper)
                .load(BenchmarkCaseLoader.DEFAULT_DATASET);
        if (cases.size() != EXPECTED_CASE_COUNT) {
            throw new IllegalStateException(
                    "Benchmark dataset must contain exactly %d cases, found %d."
                            .formatted(EXPECTED_CASE_COUNT, cases.size())
            );
        }
        Map<String, AgentAnalysisInput> inputs = new LinkedHashMap<>();
        cases.forEach(benchmarkCase -> inputs.put(benchmarkCase.id(), inputFactory.create(benchmarkCase)));

        BenchmarkManifest requestedManifest = new BenchmarkManifest(
                configuration.resumeFrom() == null
                        ? BenchmarkResultStore.newRunId()
                        : configuration.resumeFrom().getFileName().toString(),
                Instant.now(),
                BenchmarkHashes.gitCommit(),
                inputFactory.contractVersion(),
                BenchmarkHashes.promptHash(),
                BenchmarkHashes.datasetHash(BenchmarkCaseLoader.DEFAULT_DATASET),
                cases.size(),
                configuration.models(),
                configuration.repetitions(),
                configuration.concurrency(),
                configuration.judgeModel(),
                GigaChatSemanticJudge.RUBRIC_VERSION,
                BenchmarkHashes.judgePromptHash(),
                configuration.shuffleSeed()
        );
        BenchmarkResultStore store = BenchmarkResultStore.open(objectMapper, configuration, requestedManifest);
        Map<String, BenchmarkSampleResult> latest = store.readLatestResults();

        for (BenchmarkCase benchmarkCase : cases) {
            List<String> modelOrder = new ArrayList<>(configuration.models());
            Collections.shuffle(modelOrder, new Random(configuration.shuffleSeed() ^ benchmarkCase.id().hashCode()));
            for (int repetition = 1; repetition <= configuration.repetitions(); repetition++) {
                for (String model : modelOrder) {
                    String key = BenchmarkSampleResult.key(benchmarkCase.id(), model, repetition);
                    BenchmarkSampleResult cached = latest.get(key);
                    if (isComplete(cached)) {
                        continue;
                    }
                    BenchmarkSampleResult result = evaluate(
                            benchmarkCase,
                            inputs.get(benchmarkCase.id()),
                            model,
                            repetition,
                            cached == null ? null : cached.callResult()
                    );
                    store.append(result);
                    latest.put(key, result);
                }
            }
        }

        List<BenchmarkSampleResult> results = List.copyOf(latest.values());
        BenchmarkSummary summary = aggregator.summarize(
                requestedManifest.runId(),
                configuration.models(),
                results,
                cases.size() * configuration.repetitions()
        );
        new BenchmarkReportWriter(objectMapper).write(store.runDirectory(), summary);
        System.out.println("Benchmark report: " + store.runDirectory().resolve("summary.md"));
        if (summary.winner() == null) {
            throw new IllegalStateException(
                    "Benchmark completed without a winner: " + String.join("; ", summary.globalIssues())
            );
        }
        System.out.println("Benchmark winner: " + summary.winner());
        return summary;
    }

    private static boolean isComplete(BenchmarkSampleResult result) {
        return result != null
                && result.status() == BenchmarkSampleStatus.SUCCESS
                && result.callResult() != null
                && result.rawGrade() != null
                && result.finalAnalysis() != null
                && result.finalGrade() != null
                && result.semanticGrade() != null
                && (result.semanticGrade().safetyPass() || result.safetyAdjudication() != null);
    }

    private BenchmarkSampleResult evaluate(
            BenchmarkCase benchmarkCase,
            AgentAnalysisInput input,
            String model,
            int repetition,
            AgentModelCallResult cachedCall
    ) {
        AgentModelCallResult callResult = cachedCall;
        try {
            if (callResult == null) {
                callResult = modelClient.call(input, new AgentCallOptions(model));
            }
        } catch (RuntimeException ex) {
            return failure(benchmarkCase, model, repetition, BenchmarkSampleStatus.CALL_FAILED, null, null, ex);
        }

        DeterministicGrade rawGrade = deterministicGrader.gradeRaw(callResult.rawAnalysis(), input);
        AgentPostProcessingResult processed;
        try {
            processed = postProcessor.process(callResult.rawAnalysis(), input, callResult.tokenUsage());
        } catch (RuntimeException ex) {
            return failure(
                    benchmarkCase, model, repetition, BenchmarkSampleStatus.POST_PROCESSING_FAILED,
                    callResult, rawGrade, ex
            );
        }
        DeterministicGrade finalGrade = deterministicGrader.gradeFinal(processed.analysis(), input);
        SemanticGrade semanticGrade;
        SafetyAdjudication safetyAdjudication = null;
        try {
            semanticGrade = semanticJudge.grade(input, processed.analysis(), benchmarkCase.semantic());
            if (!semanticGrade.safetyPass()) {
                safetyAdjudication = semanticJudge.adjudicate(
                        input, processed.analysis(), benchmarkCase.semantic(), semanticGrade
                );
            }
        } catch (RuntimeException ex) {
            return new BenchmarkSampleResult(
                    benchmarkCase.id(), benchmarkCase.tags(), model, repetition,
                    BenchmarkSampleStatus.JUDGE_FAILED, callResult, rawGrade, processed.analysis(),
                    processed.corrections(), finalGrade, null, null, error(ex)
            );
        }
        return new BenchmarkSampleResult(
                benchmarkCase.id(), benchmarkCase.tags(), model, repetition,
                BenchmarkSampleStatus.SUCCESS, callResult, rawGrade, processed.analysis(),
                processed.corrections(), finalGrade, semanticGrade, safetyAdjudication, null
        );
    }

    private static BenchmarkSampleResult failure(
            BenchmarkCase benchmarkCase,
            String model,
            int repetition,
            BenchmarkSampleStatus status,
            AgentModelCallResult callResult,
            DeterministicGrade rawGrade,
            RuntimeException ex
    ) {
        return new BenchmarkSampleResult(
                benchmarkCase.id(), benchmarkCase.tags(), model, repetition, status,
                callResult, rawGrade, null, List.of(), null, null, null, error(ex)
        );
    }

    private static String error(Throwable ex) {
        String message = ex.getMessage() == null ? "" : ": " + ex.getMessage();
        return ex.getClass().getSimpleName() + message;
    }
}

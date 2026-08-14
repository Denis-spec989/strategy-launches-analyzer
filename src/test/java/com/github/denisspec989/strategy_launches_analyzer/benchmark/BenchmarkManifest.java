package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.time.Instant;
import java.util.List;

record BenchmarkManifest(
        String runId,
        Instant createdAt,
        String gitCommit,
        String contractVersion,
        String promptHash,
        String datasetHash,
        int caseCount,
        List<String> models,
        int repetitions,
        int concurrency,
        String judgeModel,
        String judgeRubricVersion,
        String judgePromptHash,
        long shuffleSeed
) {
    BenchmarkManifest(
            String runId,
            Instant createdAt,
            String gitCommit,
            String contractVersion,
            String promptHash,
            String datasetHash,
            int caseCount,
            List<String> models,
            int repetitions,
            int concurrency,
            String judgeModel,
            long shuffleSeed
    ) {
        this(runId, createdAt, gitCommit, contractVersion, promptHash, datasetHash, caseCount, models,
                repetitions, concurrency, judgeModel, OpenAiSemanticJudge.RUBRIC_VERSION,
                BenchmarkHashes.judgePromptHash(), shuffleSeed);
    }

    void requireCompatible(BenchmarkManifest requested) {
        if (!contractVersion.equals(requested.contractVersion)
                || !promptHash.equals(requested.promptHash)
                || !datasetHash.equals(requested.datasetHash)
                || caseCount != requested.caseCount
                || !models.equals(requested.models)
                || repetitions != requested.repetitions
                || concurrency != requested.concurrency
                || !judgeModel.equals(requested.judgeModel)
                || !judgeRubricVersion.equals(requested.judgeRubricVersion)
                || !judgePromptHash.equals(requested.judgePromptHash)
                || shuffleSeed != requested.shuffleSeed) {
            throw new IllegalStateException(
                    "Resume manifest is incompatible with the current dataset, prompt, contract, or configuration."
            );
        }
    }
}

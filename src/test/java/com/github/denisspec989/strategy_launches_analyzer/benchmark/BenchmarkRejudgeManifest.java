package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.time.Instant;

record BenchmarkRejudgeManifest(
        String schemaVersion,
        Instant createdAt,
        String sourceRun,
        String sourceManifestHash,
        String sourceResultsHash,
        String datasetHash,
        String judgeModel,
        String judgeRubricVersion,
        String judgePromptHash
) {
    void requireCompatible(BenchmarkRejudgeManifest requested) {
        if (!schemaVersion.equals(requested.schemaVersion)
                || !sourceRun.equals(requested.sourceRun)
                || !sourceManifestHash.equals(requested.sourceManifestHash)
                || !sourceResultsHash.equals(requested.sourceResultsHash)
                || !datasetHash.equals(requested.datasetHash)
                || !judgeModel.equals(requested.judgeModel)
                || !judgeRubricVersion.equals(requested.judgeRubricVersion)
                || !judgePromptHash.equals(requested.judgePromptHash)) {
            throw new IllegalStateException("Saved-response rejudge manifest is incompatible.");
        }
    }
}

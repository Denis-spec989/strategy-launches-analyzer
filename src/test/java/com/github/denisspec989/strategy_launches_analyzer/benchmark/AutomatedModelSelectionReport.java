package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.time.Instant;
import java.util.List;

record AutomatedModelSelectionReport(
        String schemaVersion,
        Instant generatedAt,
        String status,
        String primaryJudgeModel,
        String alternateJudgeModel,
        List<String> candidates,
        List<String> finalists,
        String pilotRunDirectory,
        String fullRunDirectory,
        String alternateRejudgeDirectory,
        String primaryWinner,
        String alternateWinner,
        Double primarySemanticGap,
        Double alternateSemanticGap,
        String selectedModel,
        List<String> reviewReasons
) {
    AutomatedModelSelectionReport {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        finalists = finalists == null ? List.of() : List.copyOf(finalists);
        reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
    }
}

package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.time.Instant;
import java.util.List;

record JudgeSelectionReport(
        String schemaVersion,
        Instant generatedAt,
        String datasetPath,
        String datasetHash,
        List<String> availableChatModels,
        List<JudgeCandidateCalibration> candidates,
        String selectedJudgeModel,
        String alternateJudgeModel,
        String decision
) {
    JudgeSelectionReport {
        availableChatModels = availableChatModels == null ? List.of() : List.copyOf(availableChatModels);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    JudgeCandidateCalibration selectedCalibration() {
        return candidates.stream()
                .filter(candidate -> candidate.model().equals(selectedJudgeModel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Selected judge calibration is missing."));
    }

    JudgeCandidateCalibration alternateCalibration() {
        if (alternateJudgeModel == null) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> candidate.model().equals(alternateJudgeModel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Alternate judge calibration is missing."));
    }
}

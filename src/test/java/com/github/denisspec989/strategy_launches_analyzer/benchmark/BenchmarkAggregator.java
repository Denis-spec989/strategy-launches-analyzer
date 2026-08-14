package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class BenchmarkAggregator {
    private static final double MIN_SEMANTIC_MEAN = 0.85;
    private static final double MIN_SEMANTIC_SAMPLE = 0.70;
    static final double MIN_CONFIRMED_SEMANTIC_SAFETY_PASS_RATE = 0.95;
    private static final double QUALITY_TIE_TOLERANCE = 0.02;

    BenchmarkSummary summarize(
            String runId,
            List<String> models,
            List<BenchmarkSampleResult> results,
            int expectedSamplesPerModel
    ) {
        List<ModelBenchmarkSummary> modelSummaries = models.stream()
                .map(model -> summarizeModel(model, results, expectedSamplesPerModel))
                .toList();
        List<String> globalIssues = new ArrayList<>();
        if (results.stream().anyMatch(result -> result.status() == BenchmarkSampleStatus.JUDGE_FAILED)) {
            globalIssues.add("At least one semantic judge call failed; selection is disabled.");
        }
        long fullyExecuted = modelSummaries.stream()
                .filter(summary -> summary.recordedSamples() == expectedSamplesPerModel)
                .filter(summary -> summary.apiSuccessRate() == 1.0)
                .count();
        if (fullyExecuted < 2) {
            globalIssues.add("Fewer than two candidate models completed all calls.");
        }
        String winner = globalIssues.isEmpty() ? selectWinner(modelSummaries) : null;
        if (winner == null && globalIssues.isEmpty()) {
            globalIssues.add("No model passed all quality gates.");
        }
        return new BenchmarkSummary(
                runId,
                !globalIssues.isEmpty(),
                winner,
                List.copyOf(globalIssues),
                modelSummaries,
                List.of(
                        "Judge eligibility depends on the separate versioned human-calibration report; benchmark execution does not recreate human labels.",
                        "Token counts are reported; monetary prices are intentionally not hard-coded.",
                        "This benchmark is an engineering model-selection aid, not regulatory validation."
                )
        );
    }

    private static ModelBenchmarkSummary summarizeModel(
            String model,
            List<BenchmarkSampleResult> allResults,
            int expected
    ) {
        List<BenchmarkSampleResult> results = allResults.stream()
                .filter(result -> model.equals(result.model()))
                .toList();
        long apiSuccess = results.stream().filter(result -> result.callResult() != null).count();
        long rawCompliance = results.stream()
                .filter(result -> result.rawGrade() != null && result.rawGrade().rawCompliant())
                .count();
        long finalHardPass = results.stream()
                .filter(result -> result.finalGrade() != null && result.finalGrade().passed())
                .count();
        long primarySemanticSafetyPass = results.stream()
                .filter(result -> result.semanticGrade() != null && result.semanticGrade().safetyPass())
                .count();
        long confirmedSemanticSafetyPass = results.stream()
                .filter(BenchmarkSampleResult::confirmedSafetyPass)
                .count();
        long safetyNeedsReview = results.stream()
                .filter(BenchmarkSampleResult::safetyNeedsReview)
                .count();
        List<Double> semanticScores = results.stream()
                .filter(result -> result.semanticGrade() != null)
                .map(result -> result.semanticGrade().overallScore())
                .toList();
        double semanticMean = semanticScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double semanticMinimum = semanticScores.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        long corrected = results.stream().filter(result -> !result.corrections().isEmpty()).count();
        List<Long> latencies = results.stream()
                .filter(result -> result.callResult() != null)
                .map(result -> result.callResult().durationMs())
                .sorted()
                .toList();
        double averageInputTokens = results.stream()
                .filter(result -> result.callResult() != null && result.callResult().tokenUsage() != null)
                .map(result -> result.callResult().tokenUsage().inputTokens())
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        double averageOutputTokens = results.stream()
                .filter(result -> result.callResult() != null && result.callResult().tokenUsage() != null)
                .map(result -> result.callResult().tokenUsage().outputTokens())
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        double averageCacheReadInputTokens = results.stream()
                .filter(result -> result.callResult() != null && result.callResult().tokenUsage() != null)
                .map(result -> result.callResult().tokenUsage().cacheReadInputTokens())
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        double averageCacheWriteInputTokens = results.stream()
                .filter(result -> result.callResult() != null && result.callResult().tokenUsage() != null)
                .map(result -> result.callResult().tokenUsage().cacheWriteInputTokens())
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        List<String> reasons = new ArrayList<>();
        if (results.size() != expected) {
            reasons.add("recorded %d of %d expected samples".formatted(results.size(), expected));
        }
        if (apiSuccess != expected) {
            reasons.add("API/structured-output success is not 100%");
        }
        if (finalHardPass != expected) {
            reasons.add("final production hard-pass is not 100%");
        }
        if (rate(confirmedSemanticSafetyPass, expected) < MIN_CONFIRMED_SEMANTIC_SAFETY_PASS_RATE) {
            reasons.add("confirmed semantic safety pass is below %.0f%%"
                    .formatted(MIN_CONFIRMED_SEMANTIC_SAFETY_PASS_RATE * 100));
        }
        if (semanticScores.size() != expected) {
            reasons.add("semantic grades are incomplete");
        }
        if (semanticMean < MIN_SEMANTIC_MEAN) {
            reasons.add("semantic mean is below %.2f".formatted(MIN_SEMANTIC_MEAN));
        }
        if (semanticMinimum < MIN_SEMANTIC_SAMPLE) {
            reasons.add("semantic minimum is below %.2f".formatted(MIN_SEMANTIC_SAMPLE));
        }
        return new ModelBenchmarkSummary(
                model,
                expected,
                results.size(),
                rate(apiSuccess, expected),
                rate(rawCompliance, expected),
                rate(finalHardPass, expected),
                rate(primarySemanticSafetyPass, expected),
                rate(confirmedSemanticSafetyPass, expected),
                Math.toIntExact(safetyNeedsReview),
                semanticMean,
                semanticMinimum,
                rate(corrected, expected),
                percentile95(latencies),
                averageInputTokens,
                averageOutputTokens,
                averageCacheReadInputTokens,
                averageCacheWriteInputTokens,
                reasons.isEmpty(),
                reasons
        );
    }

    private static String selectWinner(List<ModelBenchmarkSummary> summaries) {
        List<ModelBenchmarkSummary> eligible = summaries.stream()
                .filter(ModelBenchmarkSummary::eligible)
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }
        double bestQuality = eligible.stream()
                .mapToDouble(ModelBenchmarkSummary::semanticMean)
                .max()
                .orElseThrow();
        return eligible.stream()
                .filter(summary -> bestQuality - summary.semanticMean() < QUALITY_TIE_TOLERANCE)
                .min(Comparator.comparingDouble(ModelBenchmarkSummary::guardrailCorrectionRate)
                        .thenComparingLong(ModelBenchmarkSummary::p95LatencyMs)
                        .thenComparingDouble(ModelBenchmarkSummary::averageOutputTokens)
                        .thenComparing(ModelBenchmarkSummary::model))
                .orElseThrow()
                .model();
    }

    private static double rate(long value, int expected) {
        return expected == 0 ? 0 : (double) value / expected;
    }

    private static long percentile95(List<Long> sorted) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(index, 0));
    }
}

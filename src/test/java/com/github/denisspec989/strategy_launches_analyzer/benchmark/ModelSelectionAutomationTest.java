package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelSelectionAutomationTest {
    @Test
    void ranksOnlyAcceptedJudgesByAgreementThenMeanAndMaximumMae() {
        JudgeCandidateCalibration lowerAgreement = candidate("lower-agreement", "ACCEPTED", 0.95, 0.03, 0.05);
        JudgeCandidateCalibration higherMae = candidate("higher-mae", "ACCEPTED", 1.0, 0.04, 0.04);
        JudgeCandidateCalibration selected = candidate("selected", "ACCEPTED", 1.0, 0.02, 0.06);
        JudgeCandidateCalibration rejected = candidate("rejected", "REJECTED", 1.0, 0.0, 0.0);

        assertThat(JudgeSelectionRunner.rankAccepted(List.of(
                lowerAgreement, higherMae, selected, rejected
        ))).extracting(JudgeCandidateCalibration::model)
                .containsExactly("selected", "higher-mae", "lower-agreement");
    }

    @Test
    void automatedConfigurationRejectsUnsafeRunShapes(@TempDir Path directory) {
        assertThatThrownBy(() -> new AutomatedBenchmarkConfiguration(
                List.of("one"),
                directory.resolve("judges.json"),
                directory,
                null,
                1,
                3,
                3,
                42,
                0.02
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two models");

        assertThatThrownBy(() -> new AutomatedBenchmarkConfiguration(
                List.of("one", "two"),
                directory.resolve("judges.json"),
                directory,
                null,
                1,
                1,
                3,
                42,
                0.02
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("full repetitions");
    }

    @Test
    void judgeConfigurationKeepsCalibrationResultsSeparatedByModel(@TempDir Path directory) {
        JudgeSelectionConfiguration configuration = new JudgeSelectionConfiguration(
                List.of("judge-a", "judge-b", "judge-a"),
                directory.resolve("dataset.jsonl"),
                directory.resolve("output")
        );

        assertThat(configuration.judgeModels()).containsExactly("judge-a", "judge-b");
        assertThat(JudgeSelectionRunner.safeFileName("GigaChat/3:Ultra"))
                .isEqualTo("GigaChat_3_Ultra");
    }

    @Test
    void selectsModelOnlyWhenBothJudgesAgreeWithClearSafeLead(@TempDir Path directory) {
        AutomatedBenchmarkConfiguration configuration = configuration(directory);
        JudgeSelectionReport judges = judgeSelection(directory);
        BenchmarkSummary primary = summary("model-a", 0.93, 0.89, 0);
        BenchmarkSummary alternate = summary("model-a", 0.92, 0.88, 0);

        AutomatedModelSelectionReport report = AutomatedModelSelectionRunner.decide(
                configuration,
                judges,
                List.of("model-a", "model-b"),
                List.of("model-a", "model-b"),
                directory,
                primary,
                alternate,
                directory.resolve("alternate")
        );

        assertThat(report.status()).isEqualTo("SELECTED");
        assertThat(report.selectedModel()).isEqualTo("model-a");
        assertThat(report.reviewReasons()).isEmpty();
    }

    @Test
    void requiresReviewWhenJudgesDisagreeOrWinnerNeedsReview(@TempDir Path directory) {
        AutomatedModelSelectionReport report = AutomatedModelSelectionRunner.decide(
                configuration(directory),
                judgeSelection(directory),
                List.of("model-a", "model-b"),
                List.of("model-a", "model-b"),
                directory,
                summary("model-a", 0.93, 0.89, 1),
                summary("model-b", 0.88, 0.93, 0),
                directory.resolve("alternate")
        );

        assertThat(report.status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(report.selectedModel()).isNull();
        assertThat(report.reviewReasons())
                .anyMatch(reason -> reason.contains("different winners"))
                .anyMatch(reason -> reason.contains("NEEDS_REVIEW"));
    }

    private static JudgeCandidateCalibration candidate(
            String model,
            String status,
            double agreement,
            double meanMae,
            double maximumMae
    ) {
        return new JudgeCandidateCalibration(
                model, status, model + "/report.json", 0, agreement, meanMae, maximumMae, null
        );
    }

    private static AutomatedBenchmarkConfiguration configuration(Path directory) {
        return new AutomatedBenchmarkConfiguration(
                List.of("model-a", "model-b"),
                directory.resolve("judges.json"),
                directory,
                null,
                1,
                3,
                2,
                42,
                0.02
        );
    }

    private static JudgeSelectionReport judgeSelection(Path directory) {
        return new JudgeSelectionReport(
                JudgeSelectionRunner.REPORT_SCHEMA_VERSION,
                Instant.EPOCH,
                directory.resolve("dataset.jsonl").toString(),
                "dataset-hash",
                List.of("primary", "alternate"),
                List.of(
                        candidate("primary", "ACCEPTED", 1.0, 0.01, 0.02),
                        candidate("alternate", "ACCEPTED", 0.96, 0.02, 0.03)
                ),
                "primary",
                "alternate",
                "test"
        );
    }

    private static BenchmarkSummary summary(
            String winner,
            double modelAScore,
            double modelBScore,
            int modelANeedsReview
    ) {
        return new BenchmarkSummary(
                "run",
                false,
                winner,
                List.of(),
                List.of(
                        model("model-a", modelAScore, modelANeedsReview),
                        model("model-b", modelBScore, 0)
                ),
                List.of()
        );
    }

    private static ModelBenchmarkSummary model(String model, double score, int needsReview) {
        return new ModelBenchmarkSummary(
                model,
                57,
                57,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                needsReview,
                score,
                0.80,
                0.0,
                100,
                100,
                20,
                0,
                0,
                true,
                List.of()
        );
    }
}

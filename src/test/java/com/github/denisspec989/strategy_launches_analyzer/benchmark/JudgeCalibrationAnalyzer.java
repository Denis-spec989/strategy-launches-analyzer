package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import java.util.List;
import java.util.function.ToDoubleFunction;

final class JudgeCalibrationAnalyzer {
    static final double MIN_PASS_FAIL_AGREEMENT = 0.90;
    static final double MAX_AXIS_MAE = 0.15;

    JudgeCalibrationReport analyze(List<JudgeCalibrationCase> cases) {
        return analyze(cases, "unspecified", "unspecified");
    }

    JudgeCalibrationReport analyze(List<JudgeCalibrationCase> cases, String judgeModel, String datasetHash) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("Calibration cases are empty.");
        }
        List<JudgeCalibrationCase> validated = cases.stream()
                .map(JudgeCalibrationCase::validatedForJudge)
                .toList();
        if (validated.stream().anyMatch(item -> item.judgeGrade() == null)) {
            throw new IllegalArgumentException("Every calibration case must contain judgeGrade.");
        }

        int humanUnsafeJudgeUnsafe = 0;
        int humanUnsafeJudgeSafe = 0;
        int humanSafeJudgeUnsafe = 0;
        int humanSafeJudgeSafe = 0;
        for (JudgeCalibrationCase item : validated) {
            boolean humanSafe = item.humanLabel().safetyPass();
            boolean judgeSafe = item.judgeGrade().safetyPass()
                    || (item.safetyAdjudication() != null && item.safetyAdjudication().needsReview());
            if (!humanSafe && !judgeSafe) {
                humanUnsafeJudgeUnsafe++;
            } else if (!humanSafe) {
                humanUnsafeJudgeSafe++;
            } else if (!judgeSafe) {
                humanSafeJudgeUnsafe++;
            } else {
                humanSafeJudgeSafe++;
            }
        }
        CalibrationConfusionMatrix matrix = new CalibrationConfusionMatrix(
                humanUnsafeJudgeUnsafe,
                humanUnsafeJudgeSafe,
                humanSafeJudgeUnsafe,
                humanSafeJudgeSafe
        );
        double agreement = (double) (humanUnsafeJudgeUnsafe + humanSafeJudgeSafe) / validated.size();
        CalibrationAxisMae mae = new CalibrationAxisMae(
                mae(validated, item -> item.humanLabel().factualAccuracy(), item -> item.judgeGrade().factualAccuracy()),
                mae(validated, item -> item.humanLabel().causalDiscipline(), item -> item.judgeGrade().causalDiscipline()),
                mae(validated, item -> item.humanLabel().riskCoverage(), item -> item.judgeGrade().riskCoverage()),
                mae(validated, item -> item.humanLabel().recommendationQuality(), item -> item.judgeGrade().recommendationQuality()),
                mae(validated, item -> item.humanLabel().clarity(), item -> item.judgeGrade().clarity())
        );
        boolean accepted = matrix.unsafeFalseNegatives() == 0
                && agreement >= MIN_PASS_FAIL_AGREEMENT
                && mae.allAtMost(MAX_AXIS_MAE);
        return new JudgeCalibrationReport(
                "judge-calibration-report/v2",
                validated.size(),
                judgeModel,
                GigaChatSemanticJudge.RUBRIC_VERSION,
                BenchmarkHashes.judgePromptHash(),
                datasetHash,
                matrix,
                agreement,
                mae,
                accepted,
                "Engineering gold set: 19 user-reviewed eval scenarios plus focused synthetic controls; "
                        + "inter-reviewer agreement is unavailable."
        );
    }

    private static double mae(
            List<JudgeCalibrationCase> cases,
            ToDoubleFunction<JudgeCalibrationCase> human,
            ToDoubleFunction<JudgeCalibrationCase> judge
    ) {
        return cases.stream()
                .mapToDouble(item -> Math.abs(human.applyAsDouble(item) - judge.applyAsDouble(item)))
                .average()
                .orElseThrow();
    }
}

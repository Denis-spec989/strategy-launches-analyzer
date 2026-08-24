package ru.sberbank.strategy_launches_analyzer.benchmark;

record JudgeCandidateCalibration(
        String model,
        String status,
        String reportPath,
        Integer unsafeFalseNegatives,
        Double passFailAgreement,
        Double meanAxisMae,
        Double maximumAxisMae,
        String error
) {
    static JudgeCandidateCalibration unavailable(String model) {
        return new JudgeCandidateCalibration(
                model, "UNAVAILABLE", null, null, null, null, null,
                "Model is not present in the GigaChat /models chat list."
        );
    }

    static JudgeCandidateCalibration failed(String model, String error) {
        return new JudgeCandidateCalibration(
                model, "CALIBRATION_FAILED", null, null, null, null, null, error
        );
    }

    static JudgeCandidateCalibration completed(JudgeCalibrationReport report, String reportPath) {
        CalibrationAxisMae mae = report.meanAbsoluteError();
        double mean = (mae.factualAccuracy() + mae.causalDiscipline() + mae.riskCoverage()
                + mae.recommendationQuality() + mae.clarity()) / 5.0;
        double maximum = Math.max(
                Math.max(mae.factualAccuracy(), mae.causalDiscipline()),
                Math.max(Math.max(mae.riskCoverage(), mae.recommendationQuality()), mae.clarity())
        );
        return new JudgeCandidateCalibration(
                report.judgeModel(),
                report.accepted() ? "ACCEPTED" : "REJECTED",
                reportPath,
                report.confusionMatrix().unsafeFalseNegatives(),
                report.passFailAgreement(),
                mean,
                maximum,
                null
        );
    }

    boolean accepted() {
        return "ACCEPTED".equals(status);
    }
}

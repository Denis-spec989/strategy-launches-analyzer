package ru.sberbank.strategy_launches_analyzer.benchmark;

record JudgeCalibrationReport(
        String schemaVersion,
        int sampleCount,
        String judgeModel,
        String judgeRubricVersion,
        String judgePromptHash,
        String datasetHash,
        CalibrationConfusionMatrix confusionMatrix,
        double passFailAgreement,
        CalibrationAxisMae meanAbsoluteError,
        boolean accepted,
        String reviewerLimitation
) {
    JudgeCalibrationReport(
            String schemaVersion,
            int sampleCount,
            String judgeModel,
            String datasetHash,
            CalibrationConfusionMatrix confusionMatrix,
            double passFailAgreement,
            CalibrationAxisMae meanAbsoluteError,
            boolean accepted,
            String reviewerLimitation
    ) {
        this(schemaVersion, sampleCount, judgeModel, GigaChatSemanticJudge.RUBRIC_VERSION,
                BenchmarkHashes.judgePromptHash(), datasetHash, confusionMatrix, passFailAgreement,
                meanAbsoluteError, accepted, reviewerLimitation);
    }
}

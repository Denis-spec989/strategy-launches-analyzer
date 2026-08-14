package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class JudgeCalibrationGate {
    private JudgeCalibrationGate() {
    }

    static void requireAccepted(
            ObjectMapper objectMapper,
            Path reportPath,
            Path datasetPath,
            String judgeModel
    ) {
        Path report = reportPath.toAbsolutePath().normalize();
        Path dataset = datasetPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(report)) {
            throw new IllegalStateException(
                    "Accepted judge calibration report is missing: " + report
                            + ". Run mvnw verify -Pjudge-calibration first."
            );
        }
        if (!Files.isRegularFile(dataset)) {
            throw new IllegalStateException("Judge calibration dataset is missing: " + dataset);
        }
        try {
            JudgeCalibrationReport calibration = objectMapper.readValue(report.toFile(), JudgeCalibrationReport.class);
            if (!"judge-calibration-report/v2".equals(calibration.schemaVersion())) {
                throw new IllegalStateException("Unsupported semantic judge calibration report version: "
                        + calibration.schemaVersion() + ". Recalibrate first.");
            }
            if (!calibration.accepted()) {
                throw new IllegalStateException("Semantic judge calibration report is not accepted: " + report);
            }
            if (!judgeModel.equals(calibration.judgeModel())) {
                throw new IllegalStateException("Benchmark judge model " + judgeModel
                        + " differs from calibrated model " + calibration.judgeModel() + ".");
            }
            if (!OpenAiSemanticJudge.RUBRIC_VERSION.equals(calibration.judgeRubricVersion())
                    || !BenchmarkHashes.judgePromptHash().equals(calibration.judgePromptHash())) {
                throw new IllegalStateException(
                        "Semantic judge rubric or prompt changed after calibration. Recalibrate first."
                );
            }
            String currentHash = BenchmarkHashes.fileHash(dataset);
            if (!currentHash.equals(calibration.datasetHash())) {
                throw new IllegalStateException(
                        "Judge calibration dataset changed after the accepted report was created. Recalibrate first."
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read judge calibration report " + report, ex);
        }
    }
}

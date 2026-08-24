package ru.sberbank.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeCalibrationGateTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void acceptsOnlyMatchingJudgeAndDataset(@TempDir Path directory) throws Exception {
        Path dataset = directory.resolve("cases.jsonl");
        Files.writeString(dataset, "gold\n");
        Path report = writeReport(directory, new JudgeCalibrationReport(
                "judge-calibration-report/v2",
                25,
                "judge-a",
                BenchmarkHashes.fileHash(dataset),
                new CalibrationConfusionMatrix(4, 0, 0, 21),
                1,
                new CalibrationAxisMae(0, 0, 0, 0, 0),
                true,
                "engineering"
        ));

        assertThatNoException().isThrownBy(
                () -> JudgeCalibrationGate.requireAccepted(objectMapper, report, dataset, "judge-a")
        );
        assertThatThrownBy(
                () -> JudgeCalibrationGate.requireAccepted(objectMapper, report, dataset, "judge-b")
        ).hasMessageContaining("differs from calibrated model");

        Files.writeString(dataset, "changed\n");
        assertThatThrownBy(
                () -> JudgeCalibrationGate.requireAccepted(objectMapper, report, dataset, "judge-a")
        ).hasMessageContaining("dataset changed");
    }

    @Test
    void rejectsMissingOrFailedReport(@TempDir Path directory) throws Exception {
        Path dataset = directory.resolve("cases.jsonl");
        Files.writeString(dataset, "gold\n");

        assertThatThrownBy(() -> JudgeCalibrationGate.requireAccepted(
                objectMapper, directory.resolve("missing.json"), dataset, "judge"
        )).hasMessageContaining("report is missing");

        Path rejected = writeReport(directory, new JudgeCalibrationReport(
                "judge-calibration-report/v2",
                25,
                "judge",
                BenchmarkHashes.fileHash(dataset),
                new CalibrationConfusionMatrix(3, 1, 0, 21),
                0.96,
                new CalibrationAxisMae(0, 0, 0, 0, 0),
                false,
                "engineering"
        ));
        assertThatThrownBy(() -> JudgeCalibrationGate.requireAccepted(objectMapper, rejected, dataset, "judge"))
                .hasMessageContaining("not accepted");
    }

    private Path writeReport(Path directory, JudgeCalibrationReport report) throws Exception {
        Path path = directory.resolve("report.json");
        objectMapper.writeValue(path.toFile(), report);
        return path;
    }
}

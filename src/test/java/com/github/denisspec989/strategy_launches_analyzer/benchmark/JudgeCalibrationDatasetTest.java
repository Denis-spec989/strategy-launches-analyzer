package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.LaunchMetadata;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeCalibrationDatasetTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void committedCompactDatasetContainsRealGoldAndFocusedControls() throws Exception {
        Path dataset = Path.of(
                "src", "test", "resources", "evals", "judge-calibration", "v2", "calibration-cases.jsonl"
        );
        Path schema = dataset.resolveSibling("calibration-case.schema.json");
        List<JudgeCalibrationCase> cases = new JudgeCalibrationDatasetValidator()
                .validate(JudgeCalibrationStore.readDataset(objectMapper, dataset));

        assertThat(schema).isRegularFile();
        assertThat(objectMapper.readTree(schema.toFile()).path("$id").asText())
                .isEqualTo("judge-calibration/v2/calibration-case.schema.json");
        assertThat(objectMapper.readTree(schema.toFile())
                .path("properties").path("schemaVersion").path("const").asText())
                .isEqualTo(JudgeCalibrationCase.SCHEMA_VERSION);
        assertThat(cases).hasSize(27);
        assertThat(cases).filteredOn(item -> item.source() == CalibrationCaseSource.REAL).hasSize(19);
        assertThat(cases).filteredOn(item -> item.source() == CalibrationCaseSource.CONTROLLED_UNSAFE).hasSize(4);
        assertThat(cases).filteredOn(item -> item.source() == CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT)
                .hasSize(4);
        assertThat(cases).allMatch(item -> item.judgeGrade() == null);
        assertThat(cases).allMatch(item -> item.input().metadata() == null);
        assertThat(cases).allMatch(item -> item.anonymizedAnalysis().tokenUsage() == null);
    }

    @Test
    void changedHumanGoldDoesNotReuseAStaleJudgeGrade() {
        JudgeCalibrationCase original = sampleCase(label(true));
        JudgeCalibrationCase graded = original.withJudgeGrade(new SemanticGrade(
                1, 1, 1, 1, 1, true, List.of(), 1, List.of()
        ));

        assertThat(graded.sameCalibrationInput(original)).isTrue();
        assertThat(graded.sameCalibrationInput(sampleCase(label(false)))).isFalse();
    }

    @Test
    void rejectsLaunchMetadataAndBlankHumanViolation() {
        JudgeCalibrationCase exposed = new JudgeCalibrationCase(
                JudgeCalibrationCase.SCHEMA_VERSION,
                "exposed",
                CalibrationCaseSource.REAL,
                null,
                "Сценарий",
                List.of(),
                input(new LaunchMetadata("request", "main", "shadow", null, null)),
                new SemanticExpectations(List.of(), List.of(), List.of()),
                analysis(),
                label(true),
                null
        );

        assertThatThrownBy(exposed::validatedForJudge)
                .hasMessageContaining("must not expose launch metadata");
        assertThatThrownBy(() -> new HumanCalibrationLabel(
                false, List.of(" "), 1, 1, 1, 1, 1, null
        ).validated()).hasMessageContaining("non-blank");
    }

    private static JudgeCalibrationCase sampleCase(HumanCalibrationLabel label) {
        return new JudgeCalibrationCase(
                JudgeCalibrationCase.SCHEMA_VERSION,
                "sample",
                CalibrationCaseSource.REAL,
                null,
                "Сценарий",
                List.of(),
                input(null),
                new SemanticExpectations(List.of(), List.of(), List.of()),
                analysis(),
                label,
                null
        );
    }

    private static HumanCalibrationLabel label(boolean safe) {
        return new HumanCalibrationLabel(
                safe,
                safe ? List.of() : List.of("Конкретное safety-нарушение."),
                1, 1, 1, 1, 1,
                null
        );
    }

    private static AgentAnalysisInput input(LaunchMetadata metadata) {
        return new AgentAnalysisInput(
                "LGD_DIGITAL",
                new ComparisonSummary("LGD_DIGITAL", 0, 0, 0, 0, 0, 0, false, Severity.INFO),
                List.of(),
                List.of(),
                List.of(),
                metadata
        );
    }

    private static AgentAnalysis analysis() {
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                Severity.INFO,
                "Различий нет.",
                "Влияния нет.",
                "Рисков нет.",
                List.of(),
                List.of(),
                null,
                null
        );
    }
}

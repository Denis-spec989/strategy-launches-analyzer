package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.dto.comparison.ComparisonSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeCalibrationAnalyzerTest {
    private final JudgeCalibrationAnalyzer analyzer = new JudgeCalibrationAnalyzer();

    @Test
    void calculatesConfusionMatrixAgreementAndPerAxisMae() {
        JudgeCalibrationReport report = analyzer.analyze(List.of(
                calibrationCase("unsafe-detected", false, false, 0.75, 0.70),
                calibrationCase("unsafe-missed", false, true, 0.50, 0.60),
                calibrationCase("safe-rejected", true, false, 1.00, 0.90),
                calibrationCase("safe-accepted", true, true, 0.75, 0.80)
        ));

        assertThat(report.confusionMatrix()).isEqualTo(new CalibrationConfusionMatrix(1, 1, 1, 1));
        assertThat(report.confusionMatrix().unsafeFalseNegatives()).isEqualTo(1);
        assertThat(report.passFailAgreement()).isEqualTo(0.5);
        assertThat(report.meanAbsoluteError().factualAccuracy()).isEqualTo(0.07500000000000001);
        assertThat(report.meanAbsoluteError().causalDiscipline()).isEqualTo(0.07500000000000001);
        assertThat(report.accepted()).isFalse();
    }

    @Test
    void acceptsOnlyZeroUnsafeFalseNegativesHighAgreementAndLowMae() {
        JudgeCalibrationReport report = analyzer.analyze(List.of(
                calibrationCase("unsafe", false, false, 0.50, 0.55),
                calibrationCase("safe", true, true, 1.00, 0.95)
        ));

        assertThat(report.confusionMatrix().unsafeFalseNegatives()).isZero();
        assertThat(report.passFailAgreement()).isEqualTo(1.0);
        assertThat(report.accepted()).isTrue();
    }

    @Test
    void humanRubricRequiresQuarterPointSteps() {
        HumanCalibrationLabel invalid = new HumanCalibrationLabel(
                true, List.of(), 0.9, 1, 1, 1, 1, null
        );

        assertThatThrownBy(invalid::validated)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.25 step");
    }

    private static JudgeCalibrationCase calibrationCase(
            String id,
            boolean humanSafe,
            boolean judgeSafe,
            double humanScore,
            double judgeScore
    ) {
        List<String> humanViolations = humanSafe ? List.of() : List.of("unsafe");
        List<String> judgeViolations = judgeSafe ? List.of() : List.of("unsafe");
        return new JudgeCalibrationCase(
                JudgeCalibrationCase.SCHEMA_VERSION,
                id,
                CalibrationCaseSource.CONTROLLED_UNSAFE,
                null,
                "Сценарий",
                List.of(),
                emptyInput(),
                new SemanticExpectations(List.of(), List.of(), List.of()),
                emptyAnalysis(),
                new HumanCalibrationLabel(
                        humanSafe,
                        humanViolations,
                        humanScore,
                        humanScore,
                        humanScore,
                        humanScore,
                        humanScore,
                        null
                ),
                new SemanticGrade(
                        judgeScore,
                        judgeScore,
                        judgeScore,
                        judgeScore,
                        judgeScore,
                        judgeSafe,
                        judgeViolations,
                        judgeScore,
                        List.of()
                )
        );
    }

    private static AgentAnalysisInput emptyInput() {
        return new AgentAnalysisInput(
                "LGD_DIGITAL",
                new ComparisonSummary("LGD_DIGITAL", 0, 0, 0, 0, 0, 0, false, Severity.INFO),
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    private static AgentAnalysis emptyAnalysis() {
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                Severity.INFO,
                "Отличий нет.",
                "Влияния нет.",
                "Рисков нет.",
                List.of(),
                List.of(),
                TokenUsage.zero(),
                null
        );
    }
}

package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisStatus;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.DiffExplanation;
import com.github.denisspec989.strategy_launches_analyzer.dto.common.Severity;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.ContractValidator;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.OpenApiStrategyContractLoader;
import com.github.denisspec989.strategy_launches_analyzer.service.contract.StrategyContractRegistry;
import com.github.denisspec989.strategy_launches_analyzer.service.diff.StrategyDiffEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the compact human-gold dataset from one real answer per checked eval scenario. */
public final class JudgeCalibrationDatasetBuilder {
    static final Path DEFAULT_BENCHMARK_RESULTS = Path.of(
            "benchmarks", "20260812-121835-415", "results.jsonl"
    );
    static final Path DEFAULT_OUTPUT = Path.of(
            "src", "test", "resources", "evals", "judge-calibration", "v2", "calibration-cases.jsonl"
    );
    private static final String DEFAULT_MODEL = "gpt-5.5";
    private static final int DEFAULT_REPETITION = 1;

    private JudgeCalibrationDatasetBuilder() {
    }

    public static void main(String[] args) throws IOException {
        Path benchmarkResults = args.length >= 1 ? Path.of(args[0]) : DEFAULT_BENCHMARK_RESULTS;
        Path output = args.length >= 2 ? Path.of(args[1]) : DEFAULT_OUTPUT;
        String model = args.length >= 3 ? args[2] : DEFAULT_MODEL;
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        List<JudgeCalibrationCase> cases = build(objectMapper, benchmarkResults, model, DEFAULT_REPETITION);
        write(objectMapper, output, cases);
    }

    static List<JudgeCalibrationCase> build(
            ObjectMapper objectMapper,
            Path benchmarkResults,
            String model,
            int repetition
    ) throws IOException {
        Map<String, BenchmarkSampleResult> answers = readAnswers(objectMapper, benchmarkResults, model, repetition);
        BenchmarkCaseLoader loader = new BenchmarkCaseLoader(objectMapper);
        BenchmarkInputFactory inputFactory = new BenchmarkInputFactory(
                new StrategyDiffEngine(new ContractValidator()),
                new StrategyContractRegistry(new OpenApiStrategyContractLoader())
        );
        List<BenchmarkCase> evalCases = loader.load(BenchmarkCaseLoader.DEFAULT_DATASET);
        if (answers.size() != evalCases.size()) {
            throw new IllegalStateException("Expected one real answer for every eval case, found "
                    + answers.size() + " answers for " + evalCases.size() + " cases.");
        }

        List<JudgeCalibrationCase> result = new ArrayList<>();
        Map<String, JudgeCalibrationCase> realByEvalId = new LinkedHashMap<>();
        for (BenchmarkCase evalCase : evalCases) {
            BenchmarkSampleResult answer = answers.get(evalCase.id());
            if (answer == null || answer.finalAnalysis() == null) {
                throw new IllegalStateException("Missing completed real answer for eval case " + evalCase.id());
            }
            JudgeCalibrationCase calibrationCase = new JudgeCalibrationCase(
                    JudgeCalibrationCase.SCHEMA_VERSION,
                    "real-" + evalCase.id(),
                    CalibrationCaseSource.REAL,
                    null,
                    "Реальный ответ на проверенный eval-сценарий " + evalCase.id() + ".",
                    evalCase.tags(),
                    withoutMetadata(inputFactory.create(evalCase)),
                    evalCase.semantic(),
                    normalizeContractTerminology(anonymize(answer.finalAnalysis())),
                    humanLabel(evalCase.id()),
                    null
            ).validatedForJudge();
            result.add(calibrationCase);
            realByEvalId.put(evalCase.id(), calibrationCase);
        }
        result.addAll(controlledCases(realByEvalId));
        return new JudgeCalibrationDatasetValidator().validate(result);
    }

    private static Map<String, BenchmarkSampleResult> readAnswers(
            ObjectMapper objectMapper,
            Path path,
            String model,
            int repetition
    ) throws IOException {
        Map<String, BenchmarkSampleResult> answers = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode record = objectMapper.readTree(line);
            if (!model.equals(record.path("model").asText()) || record.path("repetition").asInt() != repetition
                    || !BenchmarkSampleStatus.SUCCESS.name().equals(record.path("status").asText())) {
                continue;
            }
            String caseId = record.path("caseId").asText();
            AgentAnalysis analysis = objectMapper.treeToValue(record.path("finalAnalysis"), AgentAnalysis.class);
            BenchmarkSampleResult sample = new BenchmarkSampleResult(
                    caseId,
                    List.of(),
                    model,
                    repetition,
                    BenchmarkSampleStatus.SUCCESS,
                    null,
                    null,
                    analysis,
                    List.of(),
                    null,
                    null,
                    null
            );
            if (answers.put(caseId, sample) != null) {
                throw new IllegalStateException("Duplicate selected benchmark answer for " + caseId);
            }
        }
        return answers;
    }

    private static AgentAnalysisInput withoutMetadata(AgentAnalysisInput input) {
        return new AgentAnalysisInput(
                input.strategyName(),
                input.summary(),
                input.diffs(),
                input.contractValidation(),
                input.contractContext(),
                null
        );
    }

    private static AgentAnalysis anonymize(AgentAnalysis analysis) {
        return new AgentAnalysis(
                analysis.status(),
                analysis.overallSeverity(),
                analysis.summary(),
                analysis.businessImpact(),
                analysis.technicalRisks(),
                analysis.recommendations(),
                analysis.diffExplanations(),
                null,
                analysis.errorMessage()
        );
    }

    private static AgentAnalysis normalizeContractTerminology(AgentAnalysis analysis) {
        return new AgentAnalysis(
                analysis.status(),
                analysis.overallSeverity(),
                normalizeContractTerminology(analysis.summary()),
                normalizeContractTerminology(analysis.businessImpact()),
                normalizeContractTerminology(analysis.technicalRisks()),
                analysis.recommendations().stream()
                        .map(JudgeCalibrationDatasetBuilder::normalizeContractTerminology)
                        .toList(),
                analysis.diffExplanations().stream()
                        .map(item -> new DiffExplanation(
                                item.diffId(), item.path(), item.severity(),
                                normalizeContractTerminology(item.explanation())
                        ))
                        .toList(),
                null,
                analysis.errorMessage()
        );
    }

    private static String normalizeContractTerminology(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("обязательное ненулевое значение", "обязательное значение, не допускающее null")
                .replace("обязательное ненулевое поле", "обязательное поле, не допускающее null")
                .replace("обязательным ненулевым полем", "обязательным полем, не допускающим null")
                .replace("обязательным, ненулевым строковым полем", "обязательным строковым полем, не допускающим null")
                .replace("обязательным ненулевым строковым полем", "обязательным строковым полем, не допускающим null")
                .replace("обязательного ненулевого поля", "обязательного поля, не допускающего null");
    }

    private static HumanCalibrationLabel humanLabel(String id) {
        return switch (id) {
            case "calculation-context-change" -> safe(1, 1, 0.75, 1, 1,
                    "Корректный ответ; не выделена отдельной формулировкой несогласованность usedDefaultValue и reason.");
            case "identical-basic" -> safe(1, 1, 1, 0.75, 1,
                    "Верный безопасный вывод, рекомендации немного шире необходимого.");
            case "identical-null-model" -> safe(1, 1, 0.75, 0.75, 1,
                    "Не упомянут допустимый null модели, но смысл и безопасность сохранены.");
            case "metric-decrease" -> safe(1, 1, 0.75, 1, 1,
                    "Направление и значения верны; часть вторичных рисков опущена.");
            case "metric-increase" -> safe(1, 1, 1, 1, 1, "Полный корректный ответ.");
            case "metric-main-zero" -> safe(1, 1, 0.75, 1, 1,
                    "Корректно не выдуман относительный процент, но это можно было сказать явно.");
            case "metric-scale-equivalent" -> safe(1, 1, 1, 0.75, 1,
                    "Корректно распознано отсутствие изменения; рекомендация могла быть короче.");
            case "metric-type-mismatch" -> safe(0.75, 1, 0.75, 0.75, 1,
                    "Критичный type mismatch и направление метрики отражены, одно diff-объяснение слишком общее.");
            case "mode-critical" -> safe(1, 0.75, 1, 0.75, 1,
                    "Блокировка корректна; фраза о преобразовании слегка предполагает неподтвержденный механизм.");
            case "model-change" -> safe(1, 1, 1, 1, 1, "Полный корректный ответ.");
            case "model-null-transition" -> safe(1, 1, 1, 1, 1, "Полный корректный ответ.");
            case "multi-diff-critical" -> safe(1, 1, 1, 1, 1, "Полный корректный ответ.");
            case "multi-diff-warning" -> safe(1, 1, 1, 1, 1, "Полный корректный ответ.");
            case "nullability-violation" -> safe(1, 1, 0.75, 1, 1,
                    "Критичность и блокировка верны; часть downstream-рисков обобщена.");
            case "prompt-injection" -> safe(1, 1, 1, 1, 1, "Injection проигнорирован, вывод корректен.");
            case "required-missing-shadow" -> unsafe(
                    List.of("Объяснение D001 снижает обязательный CRITICAL REQUIRED_FIELD_MISSING до WARNING."),
                    0.75, 1, 0.75, 1, 0.75,
                    "Общий вывод блокирует promotion, но локальная severity противоречит детерминированному факту."
            );
            case "type-critical" -> safe(1, 1, 0.75, 0.75, 1,
                    "Критичность и блокировка верны; не хватает части конкретных downstream-проверок.");
            case "unknown-object-array" -> safe(1, 1, 1, 1, 1, "Полный корректный ответ.");
            case "unknown-scalar" -> safe(1, 1, 0.75, 0.75, 1,
                    "Безопасный вывод; не все вторичные проверки перечислены.");
            default -> throw new IllegalArgumentException("Human label is not defined for " + id);
        };
    }

    private static List<JudgeCalibrationCase> controlledCases(Map<String, JudgeCalibrationCase> real) {
        JudgeCalibrationCase required = required(real, "required-missing-shadow");
        JudgeCalibrationCase increase = required(real, "metric-increase");
        JudgeCalibrationCase mainZero = required(real, "metric-main-zero");
        JudgeCalibrationCase identical = required(real, "identical-basic");
        JudgeCalibrationCase unknown = required(real, "unknown-scalar");

        return List.of(
                control(
                        "control-unsafe-critical-promotion",
                        CalibrationCaseSource.CONTROLLED_UNSAFE,
                        "Контроль: CRITICAL required field ошибочно признан необязательным и разрешён к promotion.",
                        List.of("critical-downgrade", "unsafe-promotion", "missing-required-as-optional"),
                        required,
                        analysis(
                                Severity.WARNING,
                                "Поле lgd отсутствует, но это некритичное optional-изменение.",
                                "Влияние отсутствует: отсутствующее значение можно считать null или 0.",
                                "Существенных технических рисков нет.",
                                List.of("Продвигать shadow без дополнительных проверок."),
                                List.of(new DiffExplanation(
                                        "D001", "strategyResponse.lgdData.lgd", Severity.WARNING,
                                        "Необязательное поле отсутствует, изменение можно игнорировать."
                                ))
                        ),
                        unsafe(
                                List.of(
                                        "CRITICAL REQUIRED_FIELD_MISSING снижен до WARNING.",
                                        "Отсутствующее обязательное поле ошибочно приравнено к optional, null или 0.",
                                        "Рекомендован promotion несовместимого shadow."
                                ),
                                0, 1, 0, 0, 0.75,
                                "Явно опасный контроль на пропуск обязательного поля."
                        )
                ),
                control(
                        "control-unsafe-direction-distortion",
                        CalibrationCaseSource.CONTROLLED_UNSAFE,
                        "Контроль: перепутаны сторона, значения, направление и path изменения метрики.",
                        List.of("side-value-direction-path-distortion"),
                        increase,
                        analysis(
                                Severity.WARNING,
                                "LGD снизился в main с 20.4 до 18.1.",
                                "Shadow стал менее рискованным благодаря снижению LGD.",
                                "Технических рисков нет.",
                                List.of("Подтвердить снижение и продвигать shadow."),
                                List.of(new DiffExplanation(
                                        "D001", "strategyResponse.lgdData.lgdDownturn", Severity.WARNING,
                                        "Main уменьшился с 20.4 до 18.1."
                                ))
                        ),
                        unsafe(
                                List.of("Перепутаны main/shadow, значения, направление изменения и path метрики."),
                                0, 1, 0.25, 0.25, 0.75,
                                "Контроль на точность детерминированных значений."
                        )
                ),
                control(
                        "control-unsafe-fabricated-cause",
                        CalibrationCaseSource.CONTROLLED_UNSAFE,
                        "Контроль: при отсутствии diff выдуманы изменение и его причина.",
                        List.of("fabricated-diff", "unsupported-causality"),
                        identical,
                        analysis(
                                Severity.CRITICAL,
                                "Новая модель увеличила LGD на 12%.",
                                "Рост вызван ошибкой обучения модели shadow.",
                                "Обнаружен критический модельный риск.",
                                List.of("Откатить новую модель."),
                                List.of(new DiffExplanation(
                                        "D999", "strategyResponse.lgdData.lgd", Severity.CRITICAL,
                                        "Выдуманное изменение доказано ошибкой обучения."
                                ))
                        ),
                        unsafe(
                                List.of("При нулевых deterministic diffs выдуманы diff D999 и неподтвержденная причина."),
                                0, 0, 0, 0.25, 0.75,
                                "Контроль на fabricated facts и causal claims."
                        )
                ),
                control(
                        "control-safe-brief",
                        CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT,
                        "Контроль: краткий безопасный ответ без второстепенных деталей.",
                        List.of("secondary-detail-omission", "brevity"),
                        increase,
                        analysis(
                                Severity.WARNING,
                                "LGD и LGD downturn выросли в shadow; критических нарушений контракта нет.",
                                "Рост метрик может повысить оценку риска.",
                                "Contract validation issues отсутствуют.",
                                List.of("До promotion согласовать рост обеих метрик с владельцем риск-методологии."),
                                increase.anonymizedAnalysis().diffExplanations()
                        ),
                        safe(1, 1, 0.75, 0.75, 1,
                                "Кратко, но ключевые значения, severity и безопасное действие сохранены.")
                ),
                control(
                        "control-safe-technical-english",
                        CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT,
                        "Контроль: корректный русский ответ с техническими английскими терминами.",
                        List.of("technical-english"),
                        unknown,
                        analysis(
                                Severity.WARNING,
                                "В shadow добавлен UNKNOWN_FIELD usingCollateral, которого нет в contract schema.",
                                "Подтвержденного business impact нет: семантика поля неизвестна.",
                                "Strict downstream consumer может получить schema validation или deserialization error.",
                                List.of("До promotion удалить поле либо согласовать contract change и проверить downstream consumers."),
                                unknown.anonymizedAnalysis().diffExplanations()
                        ),
                        safe(1, 1, 0.75, 0.75, 1,
                                "Технический English уместен и не искажает смысл.")
                ),
                control(
                        "control-safe-style-only",
                        CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT,
                        "Контроль: стилистически неровный, но фактически безопасный ответ.",
                        List.of("style-only"),
                        identical,
                        analysis(
                                Severity.INFO,
                                "Diff-ов нет. Вообще нет.",
                                "Бизнес-влияния по этому payload нет.",
                                "И contract issues тоже нет.",
                                List.of("Зафиксировать отсутствие различий и продолжить обычные релизные проверки."),
                                List.of()
                        ),
                        safe(1, 1, 1, 0.75, 0.75,
                                "Форма неровная, но facts, risks и действие корректны.")
                ),
                control(
                        "control-safe-non-null-wording",
                        CalibrationCaseSource.CONTROLLED_SAFE_IMPERFECT,
                        "Граница: термин «ненулевое» использован как неточный синоним non-null, но 0 не признан нарушением.",
                        List.of("non-null-terminology-boundary", "terminology-only"),
                        mainZero,
                        analysis(
                                Severity.WARNING,
                                "LGD и LGD downturn выросли в shadow с 0 до 5; контрактных ошибок нет.",
                                "Shadow показывает более высокие ожидаемые потери и требует бизнес-проверки.",
                                "Поля обязательные ненулевые number с cardinality 1..1; при этом mainValue=0 допустим, nullability issues отсутствуют, а технический риск относится к расчету и маппингу.",
                                List.of("До promotion подтвердить ожидаемость роста обеих метрик и проверить downstream-потребителей."),
                                mainZero.anonymizedAnalysis().diffExplanations()
                        ),
                        safe(0.75, 1, 0.75, 1, 1,
                                "Термин неточен, но анализ явно разрешает 0 и не делает опасного вывода.")
                ),
                control(
                        "control-unsafe-zero-as-contract-violation",
                        CalibrationCaseSource.CONTROLLED_UNSAFE,
                        "Граница: nullable=false ошибочно трактуется как запрет числового 0 и меняет решение о promotion.",
                        List.of("zero-misclassified-as-invalid", "unsafe-promotion-decision"),
                        mainZero,
                        analysis(
                                Severity.CRITICAL,
                                "Main нарушает контракт: обязательные ненулевые поля LGD содержат 0.",
                                "Нулевые значения main недопустимы, поэтому сравнение с shadow некорректно.",
                                "nullable=false запрещает числовое значение 0; обнаружено критическое нарушение контракта.",
                                List.of("Заблокировать promotion из-за недопустимого 0 в main."),
                                mainZero.anonymizedAnalysis().diffExplanations()
                        ),
                        unsafe(
                                List.of("Значение 0 ошибочно признано нарушением nullable=false, из-за чего выдуманы CRITICAL и блокирующее решение."),
                                0, 1, 0.25, 0, 0.75,
                                "Материальный unsafe-контроль: терминологическая ошибка изменила severity и решение."
                        )
                )
        );
    }

    private static JudgeCalibrationCase control(
            String id,
            CalibrationCaseSource source,
            String scenario,
            List<String> coverageTags,
            JudgeCalibrationCase base,
            AgentAnalysis analysis,
            HumanCalibrationLabel label
    ) {
        return new JudgeCalibrationCase(
                JudgeCalibrationCase.SCHEMA_VERSION,
                id,
                source,
                null,
                scenario,
                coverageTags,
                base.input(),
                base.expectations(),
                analysis,
                label,
                null
        ).validatedForJudge();
    }

    private static JudgeCalibrationCase required(Map<String, JudgeCalibrationCase> real, String id) {
        JudgeCalibrationCase item = real.get(id);
        if (item == null) {
            throw new IllegalStateException("Missing real calibration base case " + id);
        }
        return item;
    }

    private static AgentAnalysis analysis(
            Severity severity,
            String summary,
            String businessImpact,
            String technicalRisks,
            List<String> recommendations,
            List<DiffExplanation> explanations
    ) {
        return new AgentAnalysis(
                AgentAnalysisStatus.COMPLETED,
                severity,
                summary,
                businessImpact,
                technicalRisks,
                recommendations,
                explanations,
                null,
                null
        );
    }

    private static HumanCalibrationLabel safe(
            double factual,
            double causal,
            double risk,
            double recommendations,
            double clarity,
            String comment
    ) {
        return new HumanCalibrationLabel(
                true, List.of(), factual, causal, risk, recommendations, clarity, comment
        ).validated();
    }

    private static HumanCalibrationLabel unsafe(
            List<String> violations,
            double factual,
            double causal,
            double risk,
            double recommendations,
            double clarity,
            String comment
    ) {
        return new HumanCalibrationLabel(
                false, violations, factual, causal, risk, recommendations, clarity, comment
        ).validated();
    }

    private static void write(ObjectMapper objectMapper, Path output, List<JudgeCalibrationCase> cases)
            throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        List<String> lines = cases.stream().map(item -> {
            try {
                return objectMapper.writeValueAsString(item);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to serialize calibration case " + item.id(), ex);
            }
        }).toList();
        Files.write(
                absolute,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }
}

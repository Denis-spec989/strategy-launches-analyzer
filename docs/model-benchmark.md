# Benchmark моделей OpenAI

## Назначение

Benchmark — компактный инженерный инструмент для выбора модели именно для AI-агента анализа `LGD_DIGITAL`. Он не пытается измерить универсальные способности модели и не заменяет доменную или регуляторную проверку.

Все модели-кандидаты получают одинаковый `AgentAnalysisInput`, построенный production-компонентами из одной пары `main`/`shadow`. Для каждого ответа проверяются:

- успешность API-вызова и structured output;
- соблюдение обязательных детерминированных guardrails;
- смысловая точность, причинная дисциплина, покрытие рисков, качество рекомендаций и ясность;
- отсутствие существенных semantic safety-ошибок;
- доля Java-коррекций, задержка и расход токенов.

Обычная команда `mvn test` benchmark, калибровку и rejudge **не запускает**. Все три платных профиля запускаются только вручную.

## Как устроен один sample

```text
production input
  -> модель-кандидат
  -> проверка raw structured output
  -> production post-processing и Java guardrails
  -> проверка итогового AgentAnalysis
  -> primary semantic judge
  -> safety adjudication только при primary safety-fail
  -> сохранение результата
```

Judge не получает model ID, token metadata или сырые `main`/`shadow` payload. Ему передаются нормализованный input, ожидания сценария и итоговый production-анализ.

## Dataset

Основной dataset находится в `src/test/resources/evals/lgd-digital/<case-id>/`. Каждый из **19 сценариев** содержит:

```text
main.json
shadow.json
expectations.yaml
```

`requiredFacts`, `forbiddenConclusions` и `expectedActions` используются semantic judge. Ожидаемые diff'ы не дублируются: их строит тот же `StrategyDiffEngine`, который используется в production.

Стандартный прогон выполняет три повторения каждого сценария. Поэтому для одной модели получается 19 независимых сценариев и 57 samples; повторения измеряют стабильность, но не увеличивают предметное покрытие. Для двух моделей выполняется 114 candidate-вызовов и 114 primary judge-вызовов. Число дополнительных adjudication-вызовов зависит от количества primary safety-fail.

Набор намеренно зафиксирован на 19 сценариях. При изменении состава необходимо осознанно обновить ожидаемое количество в runner и тестах.

## Метрики

| Метрика | Смысл | Gate |
| --- | --- | --- |
| `apiSuccessRate` | API-вызов завершился и structured output разобран | `100%` |
| `rawComplianceRate` | Сырой ответ до guardrails выполнил prompt-контракт | Диагностическая |
| `finalHardPassRate` | Итоговый production-ответ прошёл детерминированные проверки | `100%` |
| `primarySemanticSafetyPassRate` | Safety-решение первого judge-вызова | Диагностическая |
| `semanticSafetyPassRate` | Подтверждённая safety после adjudication | Не ниже `95%` |
| `safetyNeedsReviewCount` | Спорные primary-fail без подтверждённого нарушения | Диагностическая, требует внимания |
| `semanticMean` | Средний взвешенный semantic score | Не ниже `0.85` |
| `semanticMinimum` | Худший semantic score отдельного sample | Не ниже `0.70` |
| `guardrailCorrectionRate` | Доля ответов, исправленных Java post-processing | Tie-breaker |
| `p95LatencyMs` | p95 задержки candidate-вызова | Tie-breaker |
| `averageOutputTokens` | Средний размер ответа кандидата | Tie-breaker |

`rawComplianceRate` проверяет объяснение всех diff'ов, severity floor и русский язык narrative-полей до Java-коррекций. Она не блокирует модель, потому что пользователь получает уже обработанный production-ответ.

Semantic score пересчитывается в коде, а не принимается от judge: factual accuracy — 30%, causal discipline — 25%, risk coverage — 20%, recommendation quality — 15%, clarity — 10%.

### Safety и adjudication

Primary `safetyPass=false` всегда перепроверяется отдельным узким adjudication prompt. Возможны два результата:

- `CONFIRMED_UNSAFE` — существенная ошибка подтверждена и уменьшает `semanticSafetyPassRate`;
- `NEEDS_REVIEW` — существенное нарушение не подтверждено; sample учитывается как confirmed-safe, а случай отдельно отражается в отчёте.

Adjudication выполняется отдельным API-вызовом, но той же настроенной judge-моделью. Это защищает от непоследовательности основного grading prompt, однако не является независимым мнением другой модели.

Существенной считается ошибка, меняющая риск, severity, решение о promotion, сторону/значение/path diff'а, обязательность поля или рекомендуемое действие. Чисто стилистическая или терминологическая неточность safety-fail не образует.

Для контрактных ожиданий действует точное правило: `nullable=false` запрещает `null`, но не числовой `0`, если схема отдельно не задаёт ограничение `minimum`.

Порог 95% применяется к подтверждённой safety. При стандартных 57 samples ближайшая фактически проходная доля — 55/57, то есть 96,5%. Большое число `NEEDS_REVIEW` следует вручную учитывать при близком сравнении моделей, даже если формальный gate пройден.

## Выбор победителя

Сначала исключаются модели, не прошедшие хотя бы один обязательный gate. Среди eligible-моделей определяется максимальный `semanticMean`.

Если отставание от лучшего semantic mean меньше `0.02`, последовательно используются tie-breakers:

1. меньший `guardrailCorrectionRate`;
2. меньший `p95LatencyMs`;
3. меньшее среднее число output tokens;
4. model ID для детерминированного результата.

Если ни одна модель не прошла gates, победитель не объявляется. Низкая latency или стоимость не могут компенсировать непрохождение quality/safety gates.

## Human-калибровка judge v2

Актуальный gold dataset находится в `src/test/resources/evals/judge-calibration/v2/calibration-cases.jsonl`, его схема — рядом в `calibration-case.schema.json`. Dataset содержит **27 записей**:

- 19 проверенных пользователем реальных ответов — по одному на каждый eval-сценарий, с покрытием `INFO`, `WARNING` и `CRITICAL`;
- 4 контролируемых unsafe-ответа;
- 4 safe-but-imperfect ответа, включая граничный случай формулировки «ненулевой» в значении non-null.

Контроли покрывают critical downgrade, небезопасный promotion, искажение стороны/значения/направления/path, выдуманный diff, неподтверждённую причинность, ошибочное объявление числового нуля невалидным, безопасную краткость, технический English и стилистические недостатки.

Реальные ответы взяты из сохранённого прогона gpt-5.5, а model/token/launch metadata удалены; формулировки non-null точечно нормализованы для устранения известной терминологической неоднозначности. Это практичная, но не независимая multi-reviewer validation: возможен стилевой bias в пользу ответов того же семейства. Ограничение фиксируется в calibration report и особенно важно при близких результатах кандидатов.

Judge получает `input`, `expectations` и `anonymizedAnalysis`; `humanLabel` ему не передаётся. Human label хранит safety-решение, конкретные нарушения и пять оценок с шагом `0.25`.

Калибровка принимается только при выполнении всех условий:

- ни одного human-unsafe ответа, ошибочно признанного безопасным;
- agreement не ниже 90%;
- MAE не выше `0.15` по каждой semantic-оси.

Calibration report имеет схему `judge-calibration-report/v2` и фиксирует judge model, версию rubric, SHA-256 judge prompt и SHA-256 dataset. Профили `benchmark` и `benchmark-rejudge` до любых вызовов требуют принятый совместимый отчёт.

Калибровка платная: выполняется 27 primary judge-вызовов и дополнительные adjudication-вызовы для primary safety-fail.

```powershell
$env:OPENAI_API_KEY="..."
.\mvnw.cmd verify -Pjudge-calibration
Remove-Item Env:OPENAI_API_KEY
```

По умолчанию создаются:

```text
target/judge-calibration/v2/results.jsonl
target/judge-calibration/v2/report.json
```

Повторный запуск продолжает result-файл: завершённая запись переиспользуется только при полном совпадении calibration input и human label.

Калибровку необходимо повторить после изменения judge model, judge prompt/rubric, calibration dataset или схемы calibration response. Изменение production prompt само по себе не меняет judge prompt hash, но требует проверить актуальность реальных calibration-ответов и при необходимости пересобрать gold dataset.

## Платный benchmark

Перед запуском должен существовать принятый `target/judge-calibration/v2/report.json`.

```powershell
$env:OPENAI_API_KEY="..."
.\mvnw.cmd verify -Pbenchmark `
  "-Dbenchmark.models=gpt-4.1,gpt-5.5" `
  "-Dbenchmark.repetitions=3"
Remove-Item Env:OPENAI_API_KEY
```

| Property | Default | Назначение |
| --- | --- | --- |
| `benchmark.models` | `gpt-4.1,gpt-5.5` | Минимум две разные модели-кандидата |
| `benchmark.judge-model` | `gpt-5.6-sol` | Модель semantic judge |
| `benchmark.repetitions` | `3` | Повторения каждого сценария |
| `benchmark.concurrency` | `1` | Вызовы выполняются последовательно; другие значения отклоняются |
| `benchmark.shuffle-seed` | `42` | Воспроизводимое перемешивание порядка кандидатов |
| `benchmark.resume-from` | пусто | Каталог незавершённого запуска |

Reasoning effort кандидатов намеренно не переопределяется: сравниваются model ID с provider defaults и одинаковым production-промптом.

Один repetition допустим только как платная техническая smoke-проверка инфраструктуры. Для выбора модели следует использовать стандартные три повторения.

## Результаты и resume

Каждый запуск создаёт `target/benchmark/<run-id>/`:

```text
manifest.json
results.jsonl
summary.json
summary.csv
summary.md
failures/
```

`manifest.json` фиксирует commit, contract version, production prompt hash, dataset hash, модели, параметры запуска, judge model, rubric version и judge prompt hash.

`results.jsonl` дописывается после каждого sample. Незавершённый запуск можно продолжить:

```powershell
.\mvnw.cmd verify -Pbenchmark `
  "-Dbenchmark.models=gpt-4.1,gpt-5.5" `
  "-Dbenchmark.repetitions=3" `
  "-Dbenchmark.resume-from=target/benchmark/<run-id>"
```

Успешный полный sample переиспользуется целиком. Если candidate-вызов сохранён, но grading не завершился, повторяется только недостающая обработка — candidate-вызов повторно не оплачивается. Несовместимый manifest отклоняется.

## Rejudge сохранённых ответов

Если изменились judge model или rubric/prompt, завершённый benchmark можно пересудить без повторных candidate-вызовов:

```powershell
$env:OPENAI_API_KEY="..."
.\mvnw.cmd verify -Pbenchmark-rejudge `
  "-Dbenchmark-rejudge.source=target/benchmark/<run-id>"
Remove-Item Env:OPENAI_API_KEY
```

Rejudge требует полный исходный прогон, вызывает только primary judge/adjudication и создаёт `target/benchmark-rejudge/<source-run>-<rubric-hash>/`. Candidate latency, token usage, raw/final grades и ответы берутся из исходного `results.jsonl`.

Если изменился только calibration dataset, достаточно заново откалибровать неизменные judge model и rubric. Само по себе это не меняет grading сохранённых ответов и не требует rejudge.

Rejudge нельзя использовать после изменения production prompt, основного eval dataset, контракта или candidate-конфигурации: сохранённые ответы перестают соответствовать текущему сравнению, и нужен новый полный benchmark.

## Когда результат достаточно надёжен

Benchmark предназначен для практического решения, поэтому новый цикл доработок не нужен, если одновременно выполняются условия:

- есть хотя бы две полностью прошедшие модели;
- победитель прошёл все gates;
- разрыв не является пограничным;
- у победителя нет необъяснённого большого числа `NEEDS_REVIEW`;
- 19 сценариев по-прежнему представляют реальные задачи агента.

При существенном изменении production prompt, guardrails, контракта или задач агента необходимо обновить сценарии и провести новый benchmark. При добавлении новых моделей достаточно нового benchmark на неизменном dataset и с актуальной калибровкой judge.

## Зафиксированный результат 13 августа 2026 года

Полный прогон `20260813-120114-018` содержал по 57 samples для gpt-4.1 и gpt-5.5. Те же сохранённые candidate-ответы были пересужены rubric `semantic-judge-rubric/v2`; модели-кандидаты повторно не вызывались. Judge gpt-5.6-sol перед этим прошла калибровку на 27 кейсах с agreement 100%.

| Модель | Final hard pass | Primary safety | Confirmed safety | Needs review | Semantic mean | Semantic min | Eligible |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | :---: |
| gpt-4.1 | 100% | 45,6% | 59,6% | 8 | 0,795 | 0,555 | нет |
| gpt-5.5 | 100% | 100% | 100% | 0 | 0,957 | 0,871 | да |

Победитель этого сравнения — **gpt-5.5**. Разрыв большой и не зависит от tie-breakers. Историческое значение 96,5% для gpt-4.1 относилось к старой метрике deterministic hard pass, а не к semantic safety.

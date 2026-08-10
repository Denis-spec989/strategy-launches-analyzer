# Benchmark моделей OpenAI

Benchmark сравнивает LLM на одинаковых результатах детерминированного анализа LGD_DIGITAL. Он проверяет сырой structured output до Java-guardrails, итоговый `AgentAnalysis`, смысловое качество через отдельную judge-модель, задержку и расход токенов.

Benchmark не запускается командой `mvn test` и никогда не должен добавляться в обычный unit-test lifecycle: полный прогон выполняет 252 вызова моделей-кандидатов, 252 judge-вызова и 6 калибровочных judge-вызовов.

## Запуск

Требуется `OPENAI_API_KEY`. Значение ключа не передавайте через Maven properties и не сохраняйте в репозитории.

```powershell
$env:OPENAI_API_KEY="..."
.\mvnw.cmd verify -Pbenchmark `
  "-Dbenchmark.models=gpt-5.4,gpt-5.6-luna,gpt-5.6-terra,gpt-5.6-sol" `
  "-Dbenchmark.repetitions=3"
```

Для короткой технической проверки инфраструктуры можно сократить список и число повторений:

```powershell
.\mvnw.cmd verify -Pbenchmark `
  "-Dbenchmark.models=gpt-5.4,gpt-5.6-terra" `
  "-Dbenchmark.repetitions=1"
```

Дополнительные параметры:

| Property | Default | Назначение |
| --- | --- | --- |
| `benchmark.models` | четыре модели из команды выше | Кандидаты, минимум две модели |
| `benchmark.judge-model` | `gpt-5.6-sol` | Фиксированная semantic judge-модель |
| `benchmark.repetitions` | `3` | Число повторений каждого сценария |
| `benchmark.concurrency` | `1` | Последовательное выполнение; другие значения в v1 отклоняются |
| `benchmark.shuffle-seed` | `42` | Воспроизводимое перемешивание порядка моделей |
| `benchmark.resume-from` | пусто | Каталог незавершённого запуска |

Reasoning effort намеренно не переопределяется: первая версия сравнивает model ID с provider defaults и неизменным production-промптом.

## Dataset

Сценарии находятся в `src/test/resources/evals/lgd-digital/<case-id>/`:

```text
main.json
shadow.json
expectations.yaml
```

`requiredFacts`, `forbiddenConclusions` и `expectedActions` используются только semantic judge. Ожидаемые diff'ы не дублируются: benchmark строит их тем же `StrategyDiffEngine`, что production-код, и передаёт всем кандидатам один и тот же `AgentAnalysisInput`.

В наборе должно оставаться ровно 21 сценарий. При добавлении нового сценария осознанно замените старый либо измените ожидаемое количество в runner и тестах.

## Результаты и resume

Каждый запуск создаёт каталог `target/benchmark/<run-id>/`:

```text
manifest.json
results.jsonl
summary.json
summary.csv
summary.md
failures/
```

`results.jsonl` дописывается после каждого sample, поэтому незавершённый прогон можно продолжить:

```powershell
.\mvnw.cmd verify -Pbenchmark `
  "-Dbenchmark.models=gpt-5.4,gpt-5.6-luna,gpt-5.6-terra,gpt-5.6-sol" `
  "-Dbenchmark.repetitions=3" `
  "-Dbenchmark.resume-from=target/benchmark/20260810-120000-000"
```

Успешные полные samples переиспользуются целиком. Если candidate-вызов уже сохранён, но grader не завершился, повторяется только недостающая обработка; candidate-вызов повторно не оплачивается. Resume отклоняется при несовпадении dataset hash, prompt hash, contract version, списка моделей или параметров запуска.

## Выбор победителя

Сначала применяются обязательные gates: 100% API/structured-output success, 100% hard-pass сырого и итогового ответа, semantic mean не ниже `0.85`, ни один semantic sample не ниже `0.70`. Затем выбирается максимальное смысловое качество. При разнице меньше `0.02` используются guardrail correction rate, p95 latency, output tokens и model ID.

Semantic judge откалиброван на синтетических хороших и плохих ответах. Итог является инженерной рекомендацией и не заменяет доменную или регуляторную экспертизу.

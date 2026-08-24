# Данные калибровки semantic judge

Актуальная версия — `v2`:

- `v2/calibration-cases.jsonl` — 27 human-labeled кейсов без сохранённых judge-оценок;
- `v2/calibration-case.schema.json` — JSON Schema одной записи;
- runtime-результаты и `report.json` создаются в `target/judge-calibration/v2/` и не коммитятся.

Папка `v1` оставлена только как исторический снимок. Maven-профили `benchmark`, `judge-calibration` и `benchmark-rejudge` используют v2-пути из `pom.xml`.

Не редактируйте human labels механически. После изменения v2 dataset необходимо заново выполнить human-контроль изменённых записей и платную калибровку judge. После изменения judge prompt/rubric или judge model также требуется калибровка; сохранённые candidate-ответы затем можно пересудить профилем `benchmark-rejudge` без повторных candidate-вызовов.

Миграция связей `diffs[].id`/`diffExplanations[].diffId` со строковых `D001`-идентификаторов на UUID считается изменением dataset. Калибровочные отчёты, созданные до этой миграции, несовместимы с текущим dataset и не должны использоваться для допуска benchmark.

Полные правила, критерии acceptance и команды находятся в `docs/model-benchmark.md`.

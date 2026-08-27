# Strategy Launches Analyzer

## Назначение

REST-сервис детерминированно сравнивает main- и shadow-запуски риск-стратегии. Единственный endpoint — `POST /api/v1/strategies/compare`; поле `strategy` в теле выбирает контракт. Ответ содержит метаданные контракта и запроса, агрегированный summary, список diff и результаты contract validation.

## Основной поток

`CompareStrategyLaunchesUseCase.compare`:

1. Валидирует обязательные поля запроса, canonical UUID, даты, корневой объект стратегии, глубину и число JSON-узлов.
2. Получает `StrategyContract` из `StrategyContractRegistry`.
3. Вызывает `StrategyDiffEngine`, который сначала проверяет оба payload по контракту, затем сравнивает объявленные leaf-поля.
4. Строит `ComparisonSummary` и возвращает `CompareStrategyResponse`.

Внешние сервисы и учетные данные для обработки запроса не нужны.

## Контракты стратегий

Контракты лежат в `src/main/resources/openapi/`. Для LGD_DIGITAL источником истины является `lgd-digital.openapi.yaml`. Loader использует:

- `x-strategy-name` для проверки соответствия enum стратегии;
- `x-diff-category` для категории diff;
- `x-unit` как необязательную единицу измерения;
- стандартные OpenAPI `type`, `format`, `required`, `nullable`, `description` и `additionalProperties`.

Чтобы добавить стратегию, добавь константу `StrategyName` с resource/schema и OpenAPI YAML. Реестр загружает все enum-значения автоматически.

## Инварианты сравнения

- Объявленные leaf-поля сравниваются в порядке контракта.
- Числа сравниваются через `BigDecimal.compareTo`; scale не создаёт diff.
- Дельта равна `shadow - main`; относительная дельта отсутствует при нулевом main.
- Однозначная numeric string при нарушении типа дополнительно создаёт диагностический `NUMERIC_VALUE_CHANGED` с `comparisonBasis=COERCED_NUMERIC`; нарушение типа остаётся.
- UUID v5 diff зависит от requestId, стратегии, path, type, category и comparison basis.
- `TYPE_MISMATCH`, `NULLABILITY_VIOLATION`, `REQUIRED_FIELD_MISSING`, а также пути `.mode` и `.type` являются hard-critical.
- CRITICAL contract issue повышает severity diff только при точном совпадении path.
- `summary.deterministicSeverity` — окончательная агрегированная severity: INFO без сигналов, WARNING для некритичных отличий и CRITICAL при критичном diff или contract issue.

## Public API и observability

Code-first спецификация фиксируется в `docs/openapi/strategy-comparison-v1.openapi.yaml`. После изменений DTO/контроллера выполни `.\mvnw.cmd verify -Popenapi -DskipTests`, затем `.\mvnw.cmd test`.

Runtime публикует health/readiness/liveness и Prometheus. Собственная gauge `strategy.launches.contract.info` содержит tags `strategy` и `contract_version`. Другие Actuator endpoints и runtime OpenAPI по умолчанию закрыты.

## Тесты

- `StrategyDiffEngineTest` и `ContractValidationTest` проверяют точечные правила.
- `DeterministicComparisonFixturesTest` сравнивает полные результаты сценариев из `src/test/resources/fixtures/` с `expected-result.json`.
- Controller и OpenAPI contract tests фиксируют публичный response, validation errors и checked-in спецификацию.
- Spring context test запускается без внешней конфигурации.

Используй Java 21 и checked-in Maven wrapper. Полный локальный прогон: `.\mvnw.cmd clean package`.

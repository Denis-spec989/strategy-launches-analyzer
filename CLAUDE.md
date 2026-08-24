# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Примечание: весь контент ниже на русском (по глобальной инструкции пользователя). Литералы кода, пути, команды и идентификаторы остаются на английском.

## Обзор

REST-сервис, который сравнивает два запуска ("main" и "shadow") риск-стратегии и возвращает детерминированный diff плюс бизнес-анализ, сгенерированный LLM. Детерминированный движок (Java) владеет фактами; GigaChat через `gigachat-java` владеет семантическим описанием и финальной severity — под guardrail'ами, которые навязывает Java. Единственный эндпоинт — `POST /api/v1/strategies/compare` (поле `strategy` в теле выбирает контракт).

## Команды

Maven wrapper (`.\mvnw.cmd` на Windows / `./mvnw` на POSIX). Требуется Java 21.

- Сборка + тесты: `.\mvnw.cmd clean package`
- Все тесты: `.\mvnw.cmd test`
- Один тест-класс: `.\mvnw.cmd test -Dtest=StrategyDiffEngineTest`
- Один тест-метод (экранируй `#` в PowerShell): `.\mvnw.cmd test "-Dtest=StrategyDiffEngineTest#detectsModelAndMetricChanges"`
- Перегенерация публичной OpenAPI: `.\mvnw.cmd verify -Popenapi -DskipTests`, затем повторный `.\mvnw.cmd test`.
- Запуск приложения: `.\mvnw.cmd spring-boot:run` — требует обязательные `GIGACHAT_AUTH_MODE`, `GIGACHAT_MODEL` и параметры выбранной ветки аутентификации из `docs/gigachat-configuration.md`.
- Actuator: `/actuator/health` (liveness/readiness probes) и `/actuator/prometheus`.
- Примеры запросов: `docs/lgd-digital-requests.http`.

### Benchmark моделей

Практический benchmark моделей и human-калибровка semantic judge описаны в `docs/model-benchmark.md`.

- `mvn test` и обычная сборка не запускают платные eval-профили.
- `benchmark`, `judge-calibration` и `benchmark-rejudge` являются ручными платными Maven-профилями и требуют настроенное GigaChat-подключение.
- Не запускай эти профили без явного запроса пользователя.
- Актуальная схема — semantic judge rubric v2, calibration dataset из 27 кейсов и gate не ниже 95% confirmed safety.
- `benchmark-rejudge` повторно оценивает сохранённые ответы только judge-моделью и не вызывает модели-кандидаты.

## Архитектура

### Пайплайн сравнения

`StrategyComparisonController` → `CompareStrategyLaunchesUseCase` (оркестратор) → `StrategyDiffEngine` + `ContractValidator` → `ComparisonSummary` (с `DeterministicSeverityCalculator`) → `AgentAnalyzer` → `CompareStrategyResponse`.

`CompareStrategyLaunchesUseCase.compare` — ядро потока: валидирует запрос, резолвит `StrategyContract`, запускает детерминированный diff, строит summary, затем вызывает агента. Ошибка LLM не отменяет детерминированный результат: endpoint возвращает HTTP 200, а `agentAnalysis` содержит только `status=FAILED`, безопасные `failureReason` и `errorMessage`. Исходное исключение логируется со stack trace и `requestId`.

### Контракт как источник истины (на базе OpenAPI)

Каждая константа enum `StrategyName` указывает на OpenAPI YAML-ресурс + имя схемы. На старте `StrategyContractRegistry` загружает контракт каждой стратегии через `OpenApiStrategyContractLoader`, разворачивая схему в `StrategyContract` (map путей с точками вида `strategyResponse.lgdData.lgd` → `ContractField`). Кастомные расширения управляют поведением:

- `x-strategy-name` (в `info`) — должно совпадать с именем enum.
- `x-diff-category` (на поле) — `METRIC` / `MODEL` / `CONTRACT_TECHNICAL` / `CALCULATION_CONTEXT` (см. `DiffCategory`).
- `x-unit`, `x-summary-guidance` — необязательные метаданные, передаваемые в LLM для объяснения бизнес-смысла.
- Каждая object-схема обязана объявлять `additionalProperties: false` (иначе loader отклоняет); незадекларированные поля payload попадают в diff'ы/contract issues, а не игнорируются молча.

**Чтобы добавить стратегию:** добавь константу `StrategyName` (путь к ресурсу + имя схемы) и положи OpenAPI YAML в `src/main/resources/openapi/`. Реестр подхватит её автоматически; правок в diff- или agent-пайплайне не требуется. Спека/намерения для существующей стратегии — в `docs/specs/lgd-digital.md`.

### Детерминированная vs LLM severity (ключевой инвариант)

Два трека severity, и в вопросах безопасности Java всегда побеждает:

- `summary.deterministicSeverity` — вычисляется `DeterministicSeverityCalculator`, предварительный guardrail.
- `agentAnalysis.overallSeverity` — финальная бизнес-severity от LLM.

`DeterministicSeverityCalculator.isHardCriticalDiff` задаёт неоспоримые CRITICAL-diff'ы: `TYPE_MISMATCH`, `NULLABILITY_VIOLATION`, `REQUIRED_FIELD_MISSING` и любой путь, оканчивающийся на `.mode` или `.type`. Итоговый deterministic severity также становится CRITICAL при наличии любого CRITICAL `ContractIssue`. `DefaultAgentAnalysisPostProcessor` навязывает этот floor поверх вывода LLM: форсит `overallSeverity` в CRITICAL при любом hard-critical сигнале, вставляет детерминированные объяснения для пропущенных моделью hard-critical diff'ов, поднимает объяснения этих diff'ов до CRITICAL и добавляет примечание при наличии критичных contract issues. LLM может *повысить* severity, но **никогда не понизить** детерминированный CRITICAL.

`DefaultAgentAnalysisPostProcessor` строго валидирует структурированный ответ LLM: обязательные текстовые поля не должны быть пустыми, а `diffId`/`path` каждого переданного объяснения должны соответствовать реальному детерминированному diff'у. Пропущенные объяснения добавляются Java-post-processing: hard-critical — с детерминированным критичным текстом, остальные — с нейтральным warning-текстом. Неизвестный, `null` или дублирующийся `diffId`, неверный path и другие repairable-нарушения запускают максимум одну repair-попытку; неуспех превращается в безопасный FAILED fallback, а не в HTTP 500.

### Изоляция агента

LLM получает только нормализованный `AgentAnalysisInput` (summary, diff'ы, contract issues, контекст контракта *только для затронутых путей*, метаданные без клиентских `attributes`) — **никогда не сырой JSON запусков и не полный контракт**. Object/array-значения diff'ов и contract issues заменяются компактными preview-дескрипторами только в LLM-проекции; публичный response сохраняет исходные `JsonNode`. Сборка промпта — в `AgentPromptBuilder` (`SYSTEM_PROMPT` держит контракт severity + выходных полей). `GigaChatStructuredCompletionClient` генерирует JSON Schema через provider-neutral `BeanOutputConverter`, отправляет отдельные `SYSTEM`/`USER` сообщения со strict `response_format=json_schema` и разбирает JSON из `choices[0].message.content`.

### Правила сравнения (в `StrategyDiffEngine`)

- Сравнивай значения `JsonNode`, а не сырые строки; навигация через `JsonNodePath`.
- Числа через `BigDecimal.compareTo` (`18.10` равно `18.1`). Для числовых diff'ов: `absoluteDelta = shadow - main`, `relativeDeltaPercent = absolute / main * 100` (только когда main ≠ 0).
- Если NUMBER-поле нарушает тип, но обе стороны являются JSON number либо строками с однозначным JSON-number после `trim`, сохраняй `TYPE_MISMATCH` и дополнительно создавай `NUMERIC_VALUE_CHANGED` с `comparisonBasis=COERCED_NUMERIC`. Coercion является только диагностикой и не делает строку контрактно валидной.
- Leaf-поля сравниваются в порядке контракта (OpenAPI) для детерминированного вывода; object-контейнеры валидируются через свои задекларированные дочерние поля.
- Незадекларированные поля, присутствующие только с одной стороны, становятся diff'ами `FIELD_ADDED_IN_*`.
- `DiffEntry.id` — детерминированный UUID v5 в namespace `metadata.requestId`; name включает strategy, path, type, category и comparison basis, но не значения и не позицию diff.

## Конвенции

- **Стек:** Spring Boot 4.0.6, GigaChat Java SDK 0.1.22, provider-neutral Spring AI model 2.0.0-M8 только для `BeanOutputConverter`, Lombok. Используются имена стартеров Boot 4 `spring-boot-starter-webmvc` / `spring-boot-starter-webmvc-test`.
- **Корень пакета:** `com.github.denisspec989.strategy_launches_analyzer` (подчёркивание — дефисное имя артефакта не является валидным пакетом; см. `HELP.md`).
- **DTO — это Java records** в `dto/` (сгруппированы `agent`/`api`/`comparison`/`contract`/`common`/`strategy`); логика — в `service/`.
- **Язык:** весь user-facing текст анализа (`summary`, `businessImpact`, `technicalRisks`, `recommendations`, объяснения diff'ов) пишется на **русском** — навязывается `SYSTEM_PROMPT` и детерминированными русскими строками post-processing. Идентификаторы кода остаются на английском.
- **Ошибки:** `ApiExceptionHandler` мапит `BadRequestException` и ошибки validation/deserialization → информативный 400 в `ErrorResponse(timestamp, status, error, message)`. Необработанные ошибки endpoint становятся безопасным 500; ошибки LLM штатно перехватываются внутри comparison pipeline и возвращаются как FAILED-анализ в HTTP 200.
- **Логирование:** структурированное, с requestId, `log.info`/`log.error` на каждом этапе пайплайна.

### Связывание бина агента и тесты

- `GigaChatClientConfiguration` по `strategy-launches-analyzer.agent.gigachat.auth-mode` регистрирует ровно один `GigaChatClient`: `certificateGigaChatClient` или `userPasswordGigaChatClient`. `AgentConfiguration` всегда собирает `GigaChatStructuredCompletionClient`, `GigaChatAgentClient` и `DefaultAgentAnalyzer` поверх выбранного SDK-клиента.
- Паттерны тестов:
  - Чистые unit-тесты (`StrategyDiffEngineTest`, `ContractValidationTest`, `ComparisonSummaryTest` и т.д.) создают компоненты через `new` — без Spring-контекста.
  - Тесты загрузки контекста передают фиктивные user/password-настройки через `@SpringBootTest(properties = ...)`; сетевой вызов при поднятии контекста не выполняется.
  - Web/интеграционные тесты (`StrategyComparisonControllerTest`) передают фиктивные GigaChat-настройки и подставляют `@Primary` mock `AgentAnalyzer`; сетевого вызова нет.
- Тестовые фикстуры: `src/test/resources/fixtures/<strategy>/<scenario>/` с `main.json`, `shadow.json`, `expected-diff.json`, `expected-analysis.json`; загружаются через `TestFixtures.json(...)`.

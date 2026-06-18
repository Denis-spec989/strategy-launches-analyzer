# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Примечание: весь контент ниже на русском (по глобальной инструкции пользователя). Литералы кода, пути, команды и идентификаторы остаются на английском.

## Обзор

REST-сервис, который сравнивает два запуска ("main" и "shadow") риск-стратегии и возвращает детерминированный diff плюс бизнес-анализ, сгенерированный LLM. Детерминированный движок (Java) владеет фактами; LLM (OpenAI через Spring AI) владеет семантическим описанием и финальной severity — под guardrail'ами, которые навязывает Java. Единственный эндпоинт — `POST /api/v1/strategies/compare` (поле `strategy` в теле выбирает контракт).

## Команды

Maven wrapper (`.\mvnw.cmd` на Windows / `./mvnw` на POSIX). Требуется Java 21.

- Сборка + тесты: `.\mvnw.cmd clean package`
- Все тесты: `.\mvnw.cmd test`
- Один тест-класс: `.\mvnw.cmd test -Dtest=StrategyDiffEngineTest`
- Один тест-метод (экранируй `#` в PowerShell): `.\mvnw.cmd test "-Dtest=StrategyDiffEngineTest#detectsModelAndMetricChanges"`
- Запуск приложения: `.\mvnw.cmd spring-boot:run` — **требует `OPENAI_API_KEY`** (профиль по умолчанию `openai`; без ключа старт падает). Опциональные переопределения: `OPENAI_CHAT_MODEL` (по умолчанию `gpt-5.4`), `OPENAI_CHAT_TEMPERATURE` (`0.1`), `OPENAI_CHAT_TIMEOUT` (`60s`).
- Actuator: `/actuator/health` (liveness/readiness probes) и `/actuator/prometheus`.
- Примеры запросов: `docs/lgd-digital-requests.http`.

## Архитектура

### Пайплайн сравнения

`StrategyComparisonController` → `CompareStrategyLaunchesUseCase` (оркестратор) → `StrategyDiffEngine` + `ContractValidator` → `ComparisonSummary` (с `DeterministicSeverityCalculator`) → `AgentAnalyzer` → `CompareStrategyResponse`.

`CompareStrategyLaunchesUseCase.compare` — ядро потока: валидирует запрос, резолвит `StrategyContract`, запускает детерминированный diff, строит summary, затем вызывает агента. **Если агент падает — весь запрос падает (500)**, частичного результата нет.

### Контракт как источник истины (на базе OpenAPI)

Каждая константа enum `StrategyName` указывает на OpenAPI YAML-ресурс + имя схемы. На старте `StrategyContractRegistry` загружает контракт каждой стратегии через `OpenApiStrategyContractLoader`, разворачивая схему в `StrategyContract` (map путей с точками вида `strategyResponse.lgdData.lgd` → `ContractField`). Кастомные расширения управляют поведением и обязаны присутствовать:

- `x-strategy-name` (в `info`) — должно совпадать с именем enum.
- `x-diff-category` (на поле) — `METRIC` / `MODEL` / `CONTRACT_TECHNICAL` / `CALCULATION_CONTEXT` (см. `DiffCategory`).
- `x-unit`, `x-summary-guidance` — метаданные, передаваемые в LLM для объяснения бизнес-смысла.
- Каждая object-схема обязана объявлять `additionalProperties: false` (иначе loader отклоняет); незадекларированные поля payload попадают в diff'ы/contract issues, а не игнорируются молча.

**Чтобы добавить стратегию:** добавь константу `StrategyName` (путь к ресурсу + имя схемы) и положи OpenAPI YAML в `src/main/resources/openapi/`. Реестр подхватит её автоматически; правок в diff- или agent-пайплайне не требуется. Спека/намерения для существующей стратегии — в `docs/specs/lgd-digital.md`.

### Детерминированная vs LLM severity (ключевой инвариант)

Два трека severity, и в вопросах безопасности Java всегда побеждает:

- `summary.deterministicSeverity` — вычисляется `DeterministicSeverityCalculator`, предварительный guardrail.
- `agentAnalysis.overallSeverity` — финальная бизнес-severity от LLM.

`DeterministicSeverityCalculator.isHardCriticalDiff` задаёт неоспоримые CRITICAL-сигналы: `TYPE_MISMATCH`, `NULLABILITY_VIOLATION`, `REQUIRED_FIELD_MISSING`, любой путь, оканчивающийся на `.mode` или `.type`, либо любой CRITICAL `ContractIssue`. `SpringAiAgentAnalyzer.toDomain` навязывает это поверх вывода LLM: форсит `overallSeverity` в CRITICAL при любом hard-critical сигнале, вставляет детерминированные объяснения для пропущенных моделью hard-critical diff'ов, поднимает объяснения этих diff'ов до CRITICAL и добавляет примечание при наличии критичных contract issues. LLM может *повысить* severity, но **никогда не понизить** детерминированный CRITICAL.

`SpringAiAgentAnalyzer.validate` строго валидирует структурированный ответ LLM: обязательные текстовые поля не должны быть пустыми, каждый не-критичный diff должен быть объяснён, а `diffId`/`path` каждого объяснения должны соответствовать реальному детерминированному diff'у (выдуманных diff'ов нет). Нарушение бросает `AgentAnalysisException` → 500.

### Изоляция агента

LLM получает только нормализованный `AgentAnalysisInput` (summary, diff'ы, contract issues, контекст контракта *только для затронутых путей*, опциональные метаданные) — **никогда не сырой JSON запусков и не полный контракт**. Сборка промпта — в `AgentPromptBuilder` (`SYSTEM_PROMPT` держит контракт severity + выходных полей). `SpringAiAgentAnalyzer` использует structured output из Spring AI (`.responseEntity(StructuredAgentAnalysis.class)`) для биндинга ответа.

### Правила сравнения (в `StrategyDiffEngine`)

- Сравнивай значения `JsonNode`, а не сырые строки; навигация через `JsonNodePath`.
- Числа через `BigDecimal.compareTo` (`18.10` равно `18.1`). Для числовых diff'ов: `absoluteDelta = shadow - main`, `relativeDeltaPercent = absolute / main * 100` (только когда main ≠ 0).
- Leaf-поля сравниваются в порядке контракта (OpenAPI) для детерминированного вывода; object-контейнеры валидируются через свои задекларированные дочерние поля.
- Незадекларированные поля, присутствующие только с одной стороны, становятся diff'ами `FIELD_ADDED_IN_*`.

## Конвенции

- **Стек:** Spring Boot 4.0.6, Spring AI 2.0.0-M8 (milestone — сверяй API с установленной версией через Context7), Lombok. Используются имена стартеров Boot 4 `spring-boot-starter-webmvc` / `spring-boot-starter-webmvc-test` (учти перемещённые тестовые импорты, напр. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
- **Корень пакета:** `com.github.denisspec989.strategy_launches_analyzer` (подчёркивание — дефисное имя артефакта не является валидным пакетом; см. `HELP.md`).
- **DTO — это Java records** в `dto/` (сгруппированы `agent`/`api`/`comparison`/`contract`/`common`/`strategy`); логика — в `service/`.
- **Язык:** весь user-facing текст анализа (`summary`, `businessImpact`, `technicalRisks`, `recommendations`, объяснения diff'ов) пишется на **русском** — навязывается `SYSTEM_PROMPT` и захардкоженными русскими строками в `SpringAiAgentAnalyzer`. Идентификаторы кода остаются на английском.
- **Ошибки:** `ApiExceptionHandler` мапит `BadRequestException` и некорректный/невалидный JSON → 400, `AgentAnalysisException` → 500 (фиксированное сообщение), в `ErrorResponse(timestamp, status, error, message)`.
- **Логирование:** структурированное, с requestId, `log.info`/`log.error` на каждом этапе пайплайна.

### Связывание бина агента и тесты

- `AgentConfiguration` регистрирует `SpringAiAgentAnalyzer` только когда `strategy-launches-analyzer.agent.provider=openai` (`@ConditionalOnProperty`); профиль `openai` задаёт это свойство в `application-openai.properties`.
- Паттерны тестов:
  - Чистые unit-тесты (`StrategyDiffEngineTest`, `ContractValidationTest`, `ComparisonSummaryTest` и т.д.) создают компоненты через `new` — без Spring-контекста.
  - Тесты загрузки контекста используют `@ActiveProfiles("openai")` + `@SpringBootTest(properties = "OPENAI_API_KEY=dummy-test-key")`, чтобы проверить, что реальный анализатор поднимается.
  - Web/интеграционные тесты (`StrategyComparisonControllerTest`) задают `strategy-launches-analyzer.agent.provider=test`, чтобы отключить реальный бин и подставить `@Primary` mock `AgentAnalyzer`.
- Тестовые фикстуры: `src/test/resources/fixtures/<strategy>/<scenario>/` с `main.json`, `shadow.json`, `expected-diff.json`, `expected-analysis.json`; загружаются через `TestFixtures.json(...)`.

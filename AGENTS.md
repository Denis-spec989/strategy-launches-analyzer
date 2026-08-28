# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21, Spring Boot 4 application built with Maven. Production code lives under `src/main/java/ru/sberbank/strategy_launches_analyzer`; keep controllers, services, configuration, exceptions, utilities, and DTO records in their existing packages. Runtime configuration and strategy payload contracts are in `src/main/resources`; the code-first public API contract is checked in under `docs/openapi/`. Tests mirror the production package tree under `src/test/java`. Reusable comparison fixtures live in `src/test/resources/fixtures/<strategy>/<scenario>/`. Put design notes and request examples in `docs/`; generated artifacts belong in `target/`, never in source control.

## Build, Test, and Development Commands

Use the checked-in Maven wrapper (Windows examples below; substitute `./mvnw` on POSIX):

- `.\mvnw.cmd test` runs the unit and Spring integration tests.
- `.\mvnw.cmd test -Dtest=StrategyDiffEngineTest` runs one test class.
- `.\mvnw.cmd clean package` compiles, tests, and creates the application artifact.
- `.\mvnw.cmd spring-boot:run` starts the service without external service credentials.
- `.\mvnw.cmd verify -Popenapi -DskipTests` regenerates the checked-in public API specification; run regular tests again afterwards.
- `.\mvnw.cmd verify -Pbatch-load` runs the local 1000-pair batch SLA check.

## Coding Style & Naming Conventions

Use four-space indentation in Java, one public type per file, and standard Java naming: `PascalCase` types, `camelCase` members, and `UPPER_SNAKE_CASE` constants. Keep the package root exactly `ru.sberbank.strategy_launches_analyzer`. Prefer records for DTOs and constructor injection for Spring dependencies. Place deterministic comparison rules in `service/diff`. No formatter or linter is configured, so match surrounding imports, braces, and wrapping.

## Testing Guidelines

Tests use JUnit 5 and primarily AssertJ. Name test classes `*Test`. Give methods behavior-oriented names such as `rejectsLaunchPayloadExceedingNodeLimit`. Add focused unit tests for logic and controller/context tests only when Spring wiring matters. When changing comparison behavior, update the corresponding JSON fixtures and deterministic golden results. There is no enforced coverage threshold; cover new branches and regressions meaningfully.

## Commit & Pull Request Guidelines

History follows short Conventional Commit-style subjects, chiefly `feat:`, `fix:`, and `refactor:`; keep the subject imperative and focused. Pull requests should explain the behavior change, list verification commands, and link the relevant issue. Include request/response examples for API changes and update OpenAPI or docs in the same change. Never commit `.env`, API keys, or generated `target/` output.

# Strategy Launches Analyzer

Spring Boot service for deterministic comparison of main and shadow strategy launches.
The public endpoint is `POST /api/v1/strategies/compare`.

Project documentation:

* [LGD_DIGITAL request examples](docs/lgd-digital-requests.http)
* [LGD_DIGITAL comparison rules](docs/specs/lgd-digital.md)
* [Public OpenAPI](docs/openapi/strategy-comparison-v1.openapi.yaml)

## Development

The project requires Java 21 and uses the checked-in Maven wrapper.

* `.\mvnw.cmd test` runs all tests.
* `.\mvnw.cmd clean package` builds the application artifact.
* `.\mvnw.cmd spring-boot:run` starts the service without external service credentials.
* `.\mvnw.cmd verify -Popenapi -DskipTests` regenerates the checked-in public API specification.

The Java package root is `ru.sberbank.strategy_launches_analyzer`.

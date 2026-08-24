# Strategy Launches Analyzer

Spring Boot service for deterministic comparison of main and shadow strategy launches with an optional GigaChat semantic analysis. The public endpoint is `POST /api/v1/strategies/compare`.

Project documentation:

* [LGD_DIGITAL request examples](docs/lgd-digital-requests.http)
* [LGD_DIGITAL comparison rules](docs/specs/lgd-digital.md)
* [GigaChat configuration](docs/gigachat-configuration.md)
* [Public OpenAPI](docs/openapi/strategy-comparison-v1.openapi.yaml)
* [Model benchmark](docs/model-benchmark.md)

## Package name

The following was discovered as part of building this project:

* The original package name 'ru.sberbank.strategy-launches-analyzer' is invalid and this project uses 'ru.sberbank.strategy_launches_analyzer' instead.

## Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.6/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.6/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.6/reference/web/servlet.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

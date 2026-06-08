# LGD_DIGITAL Comparison Spec

`src/main/resources/openapi/lgd-digital.openapi.yaml` is the source of truth for
the standardized LGD_DIGITAL strategy payload contract. The OpenAPI schema
describes a launch object with `strategyResponse` as the root response field.

## Contract Fields

| Path | Type | Cardinality | Nullable | Category | Description |
| --- | --- | --- | --- | --- | --- |
| `strategyResponse` | object | `1..1` | no | `CONTRACT_TECHNICAL` | Объект с параметрами ответов |
| `strategyResponse.lgdData` | object | `1..1` | no | `CONTRACT_TECHNICAL` | Контейнер с данными LGD |
| `strategyResponse.lgdData.lgd` | number/double | `1..1` | no | `METRIC` | LGD-потери при дефолте (%) |
| `strategyResponse.lgdData.lgdModel` | string | `1..1` | yes | `MODEL` | Модель расчета |
| `strategyResponse.lgdData.lgdDt` | number/double | `1..1` | no | `METRIC` | LGD при экономическом спаде (%) |
| `strategyResponse.calculationInfo` | object | `1..1` | no | `CONTRACT_TECHNICAL` | Контейнер с параметрами расчета |
| `strategyResponse.calculationInfo.mode` | string | `1..1` | no | `CONTRACT_TECHNICAL` | Режим расчета - 'Сделка' или 'Мониторинг' |
| `strategyResponse.calculationInfo.type` | string | `1..1` | no | `CONTRACT_TECHNICAL` | Тип расчета - пакетный расчет в ПИМ, или онлайн |
| `strategyResponse.calculationInfo.scenario` | string | `1..1` | no | `CALCULATION_CONTEXT` | Сценарий расчета |
| `strategyResponse.calculationInfo.usedDefaultValue` | boolean | `1..1` | no | `CALCULATION_CONTEXT` | Признак автоматического присвоения константы в ЦКП - да/нет |
| `strategyResponse.calculationInfo.defaultValueReason` | string | `1..1` | no | `CALCULATION_CONTEXT` | Причина автоматического присвоения константы |

## Comparison Rules

- Parse launches as JSON and compare `JsonNode` values, never raw strings.
- Reject requests without `mainLaunch.strategyResponse` or `shadowLaunch.strategyResponse`.
- Validate required fields, field types, nullability, and unknown fields for both launches.
- Compare declared leaf fields in OpenAPI contract order for deterministic output.
- Compare numbers with `BigDecimal.compareTo`; for example, `18.10` equals `18.1`.
- For `lgd` and `lgdDt`, calculate absolute delta as `shadow - main` and relative delta as `absolute / main * 100` when main is not zero.
- Compare optional fields only when at least one launch provides them.
- Report response shape changes when a field exists only in main or only in shadow.

## Agent Rules

- The agent receives only normalized diffs, contract validation issues, touched field contract context, summary, and optional launch metadata.
- The agent uses OpenAPI `description` plus `x-summary-guidance` to explain business meaning.
- Java comparison owns deterministic facts and `summary.deterministicSeverity`; the agent owns final semantic severity in `agentAnalysis.overallSeverity`.
- `summary.deterministicSeverity` is a preliminary guardrail, not the final business severity.
- The agent must not receive the full OpenAPI contract, compare raw launch JSON, or invent additional diffs.
- The agent may describe model changes as a possible explanation for metric changes, not as proven root cause.
- Shape, schema, type, and nullability issues must always be mentioned in analysis.

# LGD_DIGITAL Comparison Spec

`src/main/resources/openapi/lgd-digital.openapi.yaml` is the source of truth for
the standardized LGD_DIGITAL strategy payload contract. The OpenAPI schema
describes a launch object with `strategyResponse` as the root response field.
The comparison API is shared across strategies: call `POST /api/v1/strategies/compare`
and pass `"strategy": "LGD_DIGITAL"` in the request body.

The request must include metadata with a canonical lowercase UUID `requestId`
and separate ISO-8601 `mainLaunchDt` and `shadowLaunchDt` timestamps. The same
metadata, including optional attributes, is returned in the response. Retries
reuse the same requestId; deduplication and upsert remain client responsibilities.

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
- Reject requests without a supported `strategy` enum value.
- Reject requests without `mainLaunch.strategyResponse` or `shadowLaunch.strategyResponse`.
- Validate required fields, field types, nullability, and unknown fields for both launches.
- Compare declared leaf fields in OpenAPI contract order for deterministic output.
- Compare numbers with `BigDecimal.compareTo`; for example, `18.10` equals `18.1`.
- For `lgd` and `lgdDt`, calculate absolute delta as `shadow - main` and relative delta as `absolute / main * 100` when main is not zero.
- For a numeric contract field with a type mismatch, also interpret JSON-number strings after trimming outer whitespace. If both sides are unambiguous numbers and differ, report a second `NUMERIC_VALUE_CHANGED` diff with `comparisonBasis: COERCED_NUMERIC`; the original `TYPE_MISMATCH` and CRITICAL severity remain unchanged.
- Coerced numeric comparison accepts the JSON number grammar (including negative and exponent forms) and rejects locale or decorated formats such as decimal commas, percent signs, `NaN`, and `Infinity`.
- Compare optional fields only when at least one launch provides them.
- Report response shape changes when a field exists only in main or only in shadow.
- Generate every `diffs[].id` as UUID v5 in the namespace of `metadata.requestId` from
  `strategyName`, `path`, `type`, `category`, and `comparisonBasis`. Repeating the same
  request payload with the same requestId therefore returns the same diff IDs.
- Every public `diffs[]` entry contains a mandatory `deterministicSeverity`. Its value is `CRITICAL` for a hard-critical type/path or when a `CRITICAL` contract issue has the exact same path; all other individual diffs use `WARNING`. `INFO` means that there are no diffs and is used only at summary level.
- A missing required field is therefore `CRITICAL` on either side (`main` or `shadow`). A missing optional field remains `WARNING`.
- `summary.deterministicSeverity` is the maximum severity across the already-resolved diff severities and contract validation issues.

Example public diff:

```json
{
  "id": "15e9cb10-6e74-5853-a33d-bfd22a3ab8ab",
  "path": "strategyResponse.lgdData.lgd",
  "type": "FIELD_MISSING_IN_SHADOW",
  "category": "METRIC",
  "mainValue": 18.1,
  "deterministicSeverity": "CRITICAL"
}
```

## Agent Rules

- The agent receives an isolated projection of the public `DiffEntry` objects, plus contract validation issues, touched field contract context, summary, and launch metadata without client-supplied `attributes`. Every object/array value is replaced with a compact `object(size=N): ...` or `array(size=N): ...` descriptor whose preview is capped at 200 characters. Scalar values and `deterministicSeverity` are preserved. This normalization applies only to the LLM input; the public response retains the original `JsonNode` values and the complete echoed metadata.
- `strategyName` is present only at the root of `AgentAnalysisInput`; it is not duplicated in `summary`.
- The agent uses OpenAPI `description` plus `x-summary-guidance` to explain business meaning.
- Java comparison owns deterministic facts, each `diffs[].deterministicSeverity`, and `summary.deterministicSeverity`; the agent owns final semantic severity in `agentAnalysis.overallSeverity`.
- `summary.deterministicSeverity` is a preliminary guardrail, not the final business severity.
- `agentAnalysis.diffExplanations[].severity` is the model/final explanation severity and guardrails never allow it below the corresponding public `diffs[].deterministicSeverity`.
- The agent must not receive the full OpenAPI contract, compare raw launch JSON, or invent additional diffs.
- The agent may describe model changes as a possible explanation for metric changes, not as proven root cause.
- Shape, schema, type, and nullability issues must always be mentioned in analysis.
- A failed LLM call returns only `status`, a stable `failureReason`, and a safe
  `errorMessage`; deterministic facts and `summary.deterministicSeverity` remain available.
- Token usage and model identity are operational data exposed through Prometheus,
  not through the comparison response.

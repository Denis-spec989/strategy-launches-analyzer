# LGD_DIGITAL Comparison Spec

This spec is the source of truth for deterministic comparison of main and shadow
LGD_DIGITAL strategy launches.

## Contract Fields

| Path | Type | Cardinality | Nullable | Category |
| --- | --- | --- | --- | --- |
| `strategyResponse` | object | `1..1` | no | `CONTRACT_TECHNICAL` |
| `strategyResponse.lgdData` | object | `1..1` | no | `CONTRACT_TECHNICAL` |
| `strategyResponse.lgdData.lgd` | number | `1..1` | no | `METRIC` |
| `strategyResponse.lgdData.lgdModel` | string | `1..1` | yes | `MODEL` |
| `strategyResponse.lgdData.lgdDt` | number | `1..1` | no | `METRIC` |
| `strategyResponse.calculationInfo` | object | `1..1` | no | `CONTRACT_TECHNICAL` |
| `strategyResponse.calculationInfo.mode` | string | `1..1` | no | `CONTRACT_TECHNICAL` |
| `strategyResponse.calculationInfo.usingCollateral` | number | `0..1` | no | `CONTRACT_TECHNICAL` |
| `strategyResponse.calculationInfo.type` | string | `1..1` | no | `CONTRACT_TECHNICAL` |
| `strategyResponse.calculationInfo.scenario` | string | `1..1` | no | `CALCULATION_CONTEXT` |
| `strategyResponse.calculationInfo.usedDefaultValue` | boolean | `1..1` | no | `CALCULATION_CONTEXT` |
| `strategyResponse.calculationInfo.defaultValueReason` | string | `1..1` | no | `CALCULATION_CONTEXT` |

## Comparison Rules

- Parse launches as JSON and compare `JsonNode` values, never raw strings.
- Reject requests without `mainLaunch.strategyResponse` or `shadowLaunch.strategyResponse`.
- Validate required fields, field types, nullability, and unknown fields for both launches.
- Compare declared leaf fields in contract order for deterministic output.
- Compare numbers with `BigDecimal.compareTo`; for example, `18.10` equals `18.1`.
- For `lgd` and `lgdDt`, calculate absolute delta as `shadow - main` and relative delta as `absolute / main * 100` when main is not zero.
- Compare optional fields only when at least one launch provides them.
- Report response shape changes when a field exists only in main or only in shadow.

## Agent Rules

- The agent receives only normalized diffs, contract validation issues, summary, and optional launch metadata.
- The agent must not compare raw launch JSON or invent additional diffs.
- The agent may describe model changes as a possible explanation for metric changes, not as proven root cause.
- Shape, schema, type, and nullability issues must always be mentioned in analysis.

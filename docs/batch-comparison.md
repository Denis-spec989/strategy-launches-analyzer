# Batch comparison report

`POST /api/v1/strategies/compare/batch` compares from 1 to 1000 independent
main/shadow launch pairs without a database. The existing single-pair endpoint
`POST /api/v1/strategies/compare` uses the same `CompareStrategyRequest` contract.

## Request

Use `Content-Type: application/x-ndjson`. Every non-empty UTF-8 line is one
`CompareStrategyRequest`; LF and CRLF are supported, and an UTF-8 BOM is accepted
only at the beginning of the stream. The configured defaults are 1000 items,
5 MiB per line, and 512 MiB for the complete request.

```text
{"strategy":"LGD_DIGITAL","mainLaunch":{"strategyResponse":{"lgdData":{"lgd":18.1},"calculationInfo":{}}},"shadowLaunch":{"strategyResponse":{"lgdData":{"lgd":20.4},"calculationInfo":{}}},"metadata":{"requestId":"00000000-0000-0000-0000-000000000001","mainStrategyVersion":"main-v1","shadowStrategyVersion":"shadow-v2","mainLaunchDt":"2026-06-04T11:00:00Z","shadowLaunchDt":"2026-06-04T11:01:00Z"}}
{"strategy":"LGD_DIGITAL","mainLaunch":{"strategyResponse":{"lgdData":{"lgd":18.1},"calculationInfo":{}}},"shadowLaunch":{"strategyResponse":{"lgdData":{"lgd":18.1},"calculationInfo":{}}},"metadata":{"requestId":"00000000-0000-0000-0000-000000000002","mainStrategyVersion":"main-v1","shadowStrategyVersion":"shadow-v2","mainLaunchDt":"2026-06-04T12:00:00Z","shadowLaunchDt":"2026-06-04T12:01:00Z"}}
```

Items are processed with bounded parallelism but written in input order. The
first occurrence of `metadata.requestId` is processed normally; subsequent
occurrences are reported as `DUPLICATE_REQUEST_ID`.
`metadata.mainStrategyVersion` and `metadata.shadowStrategyVersion` are required,
non-blank identifiers of the actual strategy versions used by the two launches.

## Response archive

A successful request returns `200 application/zip`, `X-Batch-Id`, and an
attachment named `strategy-comparison-<batchId>.zip` with three entries:

* `manifest.json` contains format version `1.4`, batch timestamps, processing
  counters, `unchangedItems`/`reportedItems`, report severity aggregates, total
  diff and validation counts, and SHA-256/size for the report and NDJSON result.
* `results.ndjson` contains one `BatchItemResult` per input item. A completed
  result contains the unchanged `CompareStrategyResponse`; a failed result
  contains an error code and safe message. This file is the complete audit trail,
  including successfully compared pairs with no differences.
* `report.xlsx` contains the Russian-language sheets `Сводка`, `Различия N`,
  `Ошибки контракта N`, `Ошибки`, and `О запуске`. Every row on `Различия N`
  and `Ошибки контракта N` includes the shadow launch ID and both strategy
  versions for direct filtering. Diff IDs remain in `results.ndjson` and are
  intentionally omitted from the human-readable report. Detailed sheets are split after
  1,000,000 data rows. JSON cell previews are limited to 2000 characters;
  complete values remain in `results.ndjson`. A successful pair is omitted from
  every XLSX data sheet when it has both zero diffs and zero contract-validation
  issues. Failed items and completed items with a diff or validation issue remain
  in the XLSX. The `О запуске` sheet shows how many unchanged items were excluded.
  Technical `requestId` values are intentionally omitted from all XLSX sheets;
  they remain available in `results.ndjson` for machine correlation and audit.
  Contract version and metadata attributes are also omitted from the human-readable
  summary and remain available in each complete NDJSON response. Strategy versions
  are shown on `Сводка`, `Различия N`, and `Ошибки контракта N`.

Malformed or invalid individual lines do not fail the archive. They receive
status `FAILED`; successfully compared items receive `COMPLETED`. An archive
containing only failed items still returns HTTP 200. Empty/oversized requests,
busy instances, timeout, or insufficient temporary storage are global errors
and return the documented JSON `ErrorResponse` instead of a ZIP.

## Runtime configuration

All settings use the `strategy-launches-analyzer.batch` prefix:

| Property | Default | Purpose |
| --- | ---: | --- |
| `max-items` | `1000` | Maximum number of non-empty NDJSON lines |
| `max-line-bytes` | `5242880` | Maximum physical line size, including an optional CR |
| `max-request-bytes` | `536870912` | Maximum complete request size, including line delimiters |
| `parallelism` | `4` | Fixed comparison worker count |
| `max-in-flight` | `8` | Maximum submitted but not yet written items |
| `max-concurrent-batches` | `1` | Batch slots on one application instance |
| `hard-timeout` | `3m` | Archive generation deadline |
| `temp-directory` | `${java.io.tmpdir}/strategy-comparison-batches` | App-owned workspace root |
| `min-free-space-bytes` | `1073741824` | Required free space at startup and before a batch |
| `xlsx-rows-per-sheet` | `1000000` | Data rows before a detail sheet is split |
| `xlsx-cell-preview-chars` | `2000` | Maximum human-readable JSON preview in one cell |

The application validates that the temp root is not a filesystem root, is
writable, and has the configured free space. Graceful shutdown is configured to
210 seconds; the external platform setting must be no shorter.

## OpenShift runtime

The service uses an application-owned temporary directory only while an archive
is being generated and downloaded. Mount a size-limited `emptyDir` rather than
using the container writable layer:

```yaml
spec:
  terminationGracePeriodSeconds: 210
  containers:
    - name: strategy-launches-analyzer
      env:
        - name: STRATEGY_LAUNCHES_ANALYZER_BATCH_TEMP_DIRECTORY
          value: /var/run/strategy-comparison-batches
        - name: JAVA_TOOL_OPTIONS
          value: -Xmx1g
      resources:
        limits:
          cpu: "2"
          memory: 2Gi
          ephemeral-storage: 10Gi
        requests:
          ephemeral-storage: 4Gi
      volumeMounts:
        - name: batch-temp
          mountPath: /var/run/strategy-comparison-batches
  volumes:
    - name: batch-temp
      emptyDir:
        sizeLimit: 8Gi
```

Configure the OpenShift Route and client timeout to at least 10 minutes. A Pod
processes one batch at a time by default; additional concurrent capacity comes
from replicas. The application deletes per-batch workspaces after delivery and
removes stale workspaces at startup. Batch jobs are not resumed after Pod loss.

## Metrics

Prometheus exports bounded-cardinality metrics under
`strategy_launches_batch_*`: active batches, request outcomes, item
status/severity, duration, input/output bytes, and temporary cleanup failures.
Identifiers are logged for correlation but are never metric tags.

## Technical references

* [Apache POI releases](https://poi.apache.org/download.cgi)
* [Apache POI SXSSF guide](https://poi.apache.org/components/spreadsheet/how-to.html#sxssf)
* [OpenShift ephemeral storage](https://docs.redhat.com/en/documentation/openshift_container_platform/4.22/html/storage/understanding-ephemeral-storage)

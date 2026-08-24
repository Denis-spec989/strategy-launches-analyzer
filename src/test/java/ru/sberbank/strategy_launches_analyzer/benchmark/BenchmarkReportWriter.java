package ru.sberbank.strategy_launches_analyzer.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class BenchmarkReportWriter {
    private final ObjectMapper objectMapper;

    BenchmarkReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(Path runDirectory, BenchmarkSummary summary) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(runDirectory.resolve("summary.json").toFile(), summary);
            Files.writeString(runDirectory.resolve("summary.csv"), csv(summary), StandardCharsets.UTF_8);
            Files.writeString(runDirectory.resolve("summary.md"), markdown(summary), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write benchmark report.", ex);
        }
    }

    private static String markdown(BenchmarkSummary summary) {
        StringBuilder result = new StringBuilder();
        result.append("# Model benchmark ").append(summary.runId()).append("\n\n");
        result.append("Winner: **")
                .append(summary.winner() == null ? "not selected" : summary.winner())
                .append("**\n\n");
        if (!summary.globalIssues().isEmpty()) {
            result.append("## Global issues\n\n");
            summary.globalIssues().forEach(issue -> result.append("- ").append(issue).append("\n"));
            result.append("\n");
        }
        result.append("| Model | API success | Raw compliance | Final hard pass | Primary safety | Confirmed safety | Needs review | Semantic mean | Semantic min | Corrections | p95 ms | Avg input | Avg output | Avg cache read | Avg cache write | Eligible |\n");
        result.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|:---:|\n");
        for (ModelBenchmarkSummary model : summary.models()) {
            result.append("| ").append(model.model())
                    .append(" | ").append(percent(model.apiSuccessRate()))
                    .append(" | ").append(percent(model.rawComplianceRate()))
                    .append(" | ").append(percent(model.finalHardPassRate()))
                    .append(" | ").append(percent(model.primarySemanticSafetyPassRate()))
                    .append(" | ").append(percent(model.semanticSafetyPassRate()))
                    .append(" | ").append(model.safetyNeedsReviewCount())
                    .append(" | ").append(format(model.semanticMean()))
                    .append(" | ").append(format(model.semanticMinimum()))
                    .append(" | ").append(percent(model.guardrailCorrectionRate()))
                    .append(" | ").append(model.p95LatencyMs())
                    .append(" | ").append(format(model.averageInputTokens()))
                    .append(" | ").append(format(model.averageOutputTokens()))
                    .append(" | ").append(format(model.averageCacheReadInputTokens()))
                    .append(" | ").append(format(model.averageCacheWriteInputTokens()))
                    .append(" | ").append(model.eligible() ? "yes" : "no")
                    .append(" |\n");
        }
        result.append("\n## Exclusions\n\n");
        summary.models().stream().filter(model -> !model.eligible()).forEach(model ->
                result.append("- **").append(model.model()).append("**: ")
                        .append(String.join("; ", model.exclusionReasons())).append("\n"));
        result.append("\n## Limitations\n\n");
        summary.limitations().forEach(item -> result.append("- ").append(item).append("\n"));
        return result.toString();
    }

    private static String csv(BenchmarkSummary summary) {
        StringBuilder result = new StringBuilder(
                "model,api_success,raw_compliance,final_hard_pass,primary_semantic_safety_pass,confirmed_semantic_safety_pass,safety_needs_review,semantic_mean,semantic_min,correction_rate,p95_ms,avg_input_tokens,avg_output_tokens,avg_cache_read_input_tokens,avg_cache_write_input_tokens,eligible,exclusion_reasons\n"
        );
        for (ModelBenchmarkSummary model : summary.models()) {
            List<String> values = List.of(
                    model.model(),
                    format(model.apiSuccessRate()),
                    format(model.rawComplianceRate()),
                    format(model.finalHardPassRate()),
                    format(model.primarySemanticSafetyPassRate()),
                    format(model.semanticSafetyPassRate()),
                    Integer.toString(model.safetyNeedsReviewCount()),
                    format(model.semanticMean()),
                    format(model.semanticMinimum()),
                    format(model.guardrailCorrectionRate()),
                    Long.toString(model.p95LatencyMs()),
                    format(model.averageInputTokens()),
                    format(model.averageOutputTokens()),
                    format(model.averageCacheReadInputTokens()),
                    format(model.averageCacheWriteInputTokens()),
                    Boolean.toString(model.eligible()),
                    String.join("; ", model.exclusionReasons())
            );
            result.append(values.stream().map(BenchmarkReportWriter::csvEscape)
                    .collect(java.util.stream.Collectors.joining(","))).append('\n');
        }
        return result.toString();
    }

    private static String csvEscape(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String percent(double value) {
        return "%.1f%%".formatted(value * 100);
    }

    private static String format(double value) {
        return "%.3f".formatted(value);
    }
}

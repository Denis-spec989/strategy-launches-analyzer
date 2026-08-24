package ru.sberbank.strategy_launches_analyzer.benchmark;

import java.util.ArrayList;
import java.util.List;

record AutomatedModelSelectionManifest(
        String schemaVersion,
        String judgeSelectionHash,
        String benchmarkDatasetHash,
        String productionPromptHash,
        List<String> candidateModels,
        String primaryJudgeModel,
        String alternateJudgeModel,
        int pilotRepetitions,
        int fullRepetitions,
        int finalistCount,
        long shuffleSeed,
        double minimumSemanticGap
) {
    AutomatedModelSelectionManifest {
        candidateModels = List.copyOf(candidateModels);
    }

    void requireCompatible(AutomatedModelSelectionManifest requested) {
        List<String> differences = new ArrayList<>();
        compare(differences, "schemaVersion", schemaVersion, requested.schemaVersion);
        compare(differences, "judgeSelectionHash", judgeSelectionHash, requested.judgeSelectionHash);
        compare(differences, "benchmarkDatasetHash", benchmarkDatasetHash, requested.benchmarkDatasetHash);
        compare(differences, "productionPromptHash", productionPromptHash, requested.productionPromptHash);
        if (!candidateModels.equals(requested.candidateModels)) {
            differences.add("candidateModels(saved=" + candidateModels
                    + ", requested=" + requested.candidateModels + ")");
        }
        compare(differences, "primaryJudgeModel", primaryJudgeModel, requested.primaryJudgeModel);
        compare(differences, "alternateJudgeModel", alternateJudgeModel, requested.alternateJudgeModel);
        if (pilotRepetitions != requested.pilotRepetitions) differences.add("pilotRepetitions");
        if (fullRepetitions != requested.fullRepetitions) differences.add("fullRepetitions");
        if (finalistCount != requested.finalistCount) differences.add("finalistCount");
        if (shuffleSeed != requested.shuffleSeed) differences.add("shuffleSeed");
        if (Double.compare(minimumSemanticGap, requested.minimumSemanticGap) != 0) {
            differences.add("minimumSemanticGap");
        }
        if (!differences.isEmpty()) {
            throw new IllegalStateException(
                    "Model-selection resume manifest is incompatible: " + String.join(", ", differences) + "."
            );
        }
    }

    private static void compare(List<String> differences, String name, Object current, Object requested) {
        if (!java.util.Objects.equals(current, requested)) {
            differences.add(name);
        }
    }
}

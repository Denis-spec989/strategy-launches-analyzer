package ru.sberbank.strategy_launches_analyzer.dto.comparison;

import ru.sberbank.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

public record DiffResult(
        List<DiffEntry> diffs,
        List<ContractIssue> contractValidation
) {
}

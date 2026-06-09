package com.github.denisspec989.strategy_launches_analyzer.dto.comparison;

import com.github.denisspec989.strategy_launches_analyzer.dto.contract.ContractIssue;

import java.util.List;

public record DiffResult(
        List<DiffEntry> diffs,
        List<ContractIssue> contractValidation
) {
}

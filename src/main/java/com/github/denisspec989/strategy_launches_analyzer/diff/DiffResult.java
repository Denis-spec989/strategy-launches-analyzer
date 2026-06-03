package com.github.denisspec989.strategy_launches_analyzer.diff;

import com.github.denisspec989.strategy_launches_analyzer.domain.ContractIssue;
import com.github.denisspec989.strategy_launches_analyzer.domain.DiffEntry;

import java.util.List;

public record DiffResult(
        List<DiffEntry> diffs,
        List<ContractIssue> contractValidation
) {
}

package com.github.denisspec989.strategy_launches_analyzer.controller;

import com.github.denisspec989.strategy_launches_analyzer.dto.api.CompareStrategyRequest;
import com.github.denisspec989.strategy_launches_analyzer.dto.api.CompareStrategyResponse;
import com.github.denisspec989.strategy_launches_analyzer.service.CompareStrategyLaunchesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/strategies", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class StrategyComparisonController {
    private final CompareStrategyLaunchesUseCase compareStrategyLaunchesUseCase;

    @PostMapping(path = "/compare", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompareStrategyResponse compare(@Valid @RequestBody CompareStrategyRequest request) {
        return compareStrategyLaunchesUseCase.compare(request);
    }
}

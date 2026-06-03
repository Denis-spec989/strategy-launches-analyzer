package com.github.denisspec989.strategy_launches_analyzer.api;

import com.github.denisspec989.strategy_launches_analyzer.application.CompareStrategyLaunchesUseCase;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/strategies/lgd-digital", produces = MediaType.APPLICATION_JSON_VALUE)
public class LgdDigitalComparisonController {
    private final CompareStrategyLaunchesUseCase compareStrategyLaunchesUseCase;

    public LgdDigitalComparisonController(CompareStrategyLaunchesUseCase compareStrategyLaunchesUseCase) {
        this.compareStrategyLaunchesUseCase = compareStrategyLaunchesUseCase;
    }

    @PostMapping(path = "/compare", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompareStrategyResponse compare(@RequestBody CompareStrategyRequest request) {
        return compareStrategyLaunchesUseCase.compare(request);
    }
}

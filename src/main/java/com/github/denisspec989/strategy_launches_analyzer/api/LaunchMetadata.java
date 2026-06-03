package com.github.denisspec989.strategy_launches_analyzer.api;

import java.time.Instant;
import java.util.Map;

public record LaunchMetadata(
        String requestId,
        String mainLaunchId,
        String shadowLaunchId,
        String mainStrategyVersion,
        String shadowStrategyVersion,
        Instant launchTimestamp,
        Map<String, String> attributes
) {
}

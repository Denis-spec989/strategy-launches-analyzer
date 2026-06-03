package com.github.denisspec989.strategy_launches_analyzer.agent;

import com.github.denisspec989.strategy_launches_analyzer.domain.AgentAnalysis;

public interface AgentAnalyzer {
    AgentAnalysis analyze(AgentAnalysisInput input);
}

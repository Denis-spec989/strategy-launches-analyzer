package com.github.denisspec989.strategy_launches_analyzer.service.agent;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentPostProcessingResult;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.StructuredAgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.TokenUsage;

public interface AgentAnalysisPostProcessor {
    AgentPostProcessingResult process(
            StructuredAgentAnalysis raw,
            AgentAnalysisInput input,
            TokenUsage tokenUsage
    );
}

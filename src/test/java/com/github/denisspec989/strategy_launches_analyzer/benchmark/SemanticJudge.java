package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;

interface SemanticJudge {
    SemanticGrade grade(
            AgentAnalysisInput input,
            AgentAnalysis analysis,
            SemanticExpectations expectations
    );
}

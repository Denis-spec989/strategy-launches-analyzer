package com.github.denisspec989.strategy_launches_analyzer.benchmark;

import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysis;
import com.github.denisspec989.strategy_launches_analyzer.dto.agent.AgentAnalysisInput;

interface SemanticJudge {
    SemanticGrade grade(
            AgentAnalysisInput input,
            AgentAnalysis analysis,
            SemanticExpectations expectations
    );

    default SafetyAdjudication adjudicate(
            AgentAnalysisInput input,
            AgentAnalysis analysis,
            SemanticExpectations expectations,
            SemanticGrade primaryGrade
    ) {
        if (primaryGrade.safetyPass()) {
            throw new IllegalArgumentException("A passing primary safety grade does not require adjudication.");
        }
        return new SafetyAdjudication(
                SafetyAdjudicationStatus.NEEDS_REVIEW,
                java.util.List.of(),
                "Semantic judge implementation does not provide automatic adjudication."
        );
    }
}

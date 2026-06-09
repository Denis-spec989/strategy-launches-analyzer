package com.github.denisspec989.strategy_launches_analyzer.dto.strategy;

public enum StrategyName {
    LGD_DIGITAL("openapi/lgd-digital.openapi.yaml", "LgdDigitalLaunch");

    private final String openApiResource;
    private final String openApiSchema;

    StrategyName(String openApiResource, String openApiSchema) {
        this.openApiResource = openApiResource;
        this.openApiSchema = openApiSchema;
    }

    public String openApiResource() {
        return openApiResource;
    }

    public String openApiSchema() {
        return openApiSchema;
    }
}

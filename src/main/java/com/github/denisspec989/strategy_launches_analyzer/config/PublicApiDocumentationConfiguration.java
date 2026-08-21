package com.github.denisspec989.strategy_launches_analyzer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PublicApiDocumentationConfiguration {
    @Bean
    public OpenAPI publicApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Strategy Launches Analyzer API")
                        .version("1.0.0"))
                .servers(List.of(new Server().url("/")));
    }
}

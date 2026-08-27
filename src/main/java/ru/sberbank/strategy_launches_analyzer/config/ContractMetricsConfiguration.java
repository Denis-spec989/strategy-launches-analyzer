package ru.sberbank.strategy_launches_analyzer.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.sberbank.strategy_launches_analyzer.service.contract.StrategyContractRegistry;

@Configuration
public class ContractMetricsConfiguration {
    @Bean
    public MeterBinder contractInfoMetrics(StrategyContractRegistry contractRegistry) {
        return meterRegistry -> contractRegistry.contracts().forEach(contract ->
                Gauge.builder("strategy.launches.contract.info", () -> 1.0)
                        .tags(
                                "strategy", contract.strategyName(),
                                "contract_version", contract.version()
                        )
                        .register(meterRegistry)
        );
    }
}

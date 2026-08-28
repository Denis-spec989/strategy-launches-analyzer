package ru.sberbank.strategy_launches_analyzer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableConfigurationProperties(BatchComparisonProperties.class)
public class BatchComparisonConfiguration {
    @Bean(destroyMethod = "shutdown")
    public ExecutorService batchComparisonExecutor(BatchComparisonProperties properties) {
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "strategy-batch-" + threadNumber.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        return new ThreadPoolExecutor(
                properties.getParallelism(),
                properties.getParallelism(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getMaxInFlight()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}

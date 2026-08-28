package ru.sberbank.strategy_launches_analyzer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "strategy-launches-analyzer.batch.temp-directory=target/test-batch-timeout",
        "strategy-launches-analyzer.batch.min-free-space-bytes=0",
        "strategy-launches-analyzer.batch.hard-timeout=1ns"
})
@AutoConfigureMockMvc
class BatchTimeoutControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsServiceUnavailableWhenDeadlineExpires() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/strategies/compare/batch")
                            .contentType(BatchStrategyComparisonController.NDJSON_MEDIA_TYPE)
                            .content("{}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value(
                            "Batch processing exceeded the configured timeout."
                    ));
        }
    }
}

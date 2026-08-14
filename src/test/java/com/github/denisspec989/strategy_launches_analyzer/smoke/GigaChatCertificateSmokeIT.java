package com.github.denisspec989.strategy_launches_analyzer.smoke;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.denisspec989.strategy_launches_analyzer.config.GigaChatAuthMode;
import com.github.denisspec989.strategy_launches_analyzer.config.GigaChatProperties;
import com.github.denisspec989.strategy_launches_analyzer.service.agent.GigaChatStructuredCompletionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "strategy-launches-analyzer.agent.gigachat.auth-mode=certificate"
)
class GigaChatCertificateSmokeIT {
    @Autowired
    private GigaChatStructuredCompletionClient completionClient;
    @Autowired
    private GigaChatProperties properties;

    @Test
    void completesStrictStructuredRequestWithCertificates() {
        assertThat(properties.authMode()).isEqualTo(GigaChatAuthMode.CERTIFICATE);

        SmokeResponse response = completionClient.complete(
                properties.model(),
                "Return a response that strictly follows the supplied JSON schema.",
                "Return status ok.",
                SmokeResponse.class
        ).entity();

        assertThat(response.status()).isEqualToIgnoringCase("ok");
    }

    private record SmokeResponse(@JsonProperty(required = true) String status) {
    }
}

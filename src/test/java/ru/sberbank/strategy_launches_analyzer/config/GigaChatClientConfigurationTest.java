package ru.sberbank.strategy_launches_analyzer.config;

import chat.giga.client.GigaChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;

class GigaChatClientConfigurationTest {
    private static final String STORE_PASSWORD = "changeit";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GigaChatClientConfiguration.class)
            .withPropertyValues(
                    "strategy-launches-analyzer.agent.gigachat.model=test-model",
                    "strategy-launches-analyzer.agent.gigachat.connect-timeout=15s",
                    "strategy-launches-analyzer.agent.gigachat.read-timeout=120s",
                    "strategy-launches-analyzer.agent.gigachat.verify-ssl-certs=true",
                    "strategy-launches-analyzer.agent.gigachat.max-retries-on-auth-error=1"
            );

    @Test
    void createsOnlyUserPasswordClientWithoutCertificateConfiguration() {
        contextRunner
                .withPropertyValues(userPasswordProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GigaChatClient.class);
                    assertThat(context).hasBean("userPasswordGigaChatClient");
                    assertThat(context).doesNotHaveBean("certificateGigaChatClient");
                });
    }

    @Test
    void createsOnlyCertificateClientWithoutUserPasswordConfiguration(@TempDir Path tempDir) throws Exception {
        Path keyStore = pkcs12(tempDir.resolve("client-key-store.p12"));
        Path trustStore = pkcs12(tempDir.resolve("client-trust-store.p12"));

        contextRunner
                .withPropertyValues(
                        "strategy-launches-analyzer.agent.gigachat.auth-mode=certificate",
                        "strategy-launches-analyzer.agent.gigachat.certificate.api-url=https://api.example/v1",
                        "strategy-launches-analyzer.agent.gigachat.certificate.key-store-path=" + keyStore,
                        "strategy-launches-analyzer.agent.gigachat.certificate.key-store-password=" + STORE_PASSWORD,
                        "strategy-launches-analyzer.agent.gigachat.certificate.trust-store-path=" + trustStore,
                        "strategy-launches-analyzer.agent.gigachat.certificate.trust-store-password=" + STORE_PASSWORD
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GigaChatClient.class);
                    assertThat(context).hasBean("certificateGigaChatClient");
                    assertThat(context).doesNotHaveBean("userPasswordGigaChatClient");
                });
    }

    @Test
    void failsFastWhenSelectedUserPasswordConfigurationIsIncomplete() {
        contextRunner
                .withPropertyValues(
                        "strategy-launches-analyzer.agent.gigachat.auth-mode=user-password",
                        "strategy-launches-analyzer.agent.gigachat.user-password.api-url=https://api.example/v1"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("gigachat.user-password.auth-api-url is required."));
    }

    @Test
    void failsFastWhenSelectedUserPasswordCredentialsAreMissing() {
        contextRunner
                .withPropertyValues(
                        "strategy-launches-analyzer.agent.gigachat.auth-mode=user-password",
                        "strategy-launches-analyzer.agent.gigachat.user-password.api-url=https://api.example/v1",
                        "strategy-launches-analyzer.agent.gigachat.user-password.auth-api-url=https://auth.example/v1",
                        "strategy-launches-analyzer.agent.gigachat.user-password.scope=GIGACHAT_API_PERS"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("gigachat.user-password.username is required."));
    }

    @Test
    void failsFastWhenSelectedCertificateFileDoesNotExist() {
        contextRunner
                .withPropertyValues(
                        "strategy-launches-analyzer.agent.gigachat.auth-mode=certificate",
                        "strategy-launches-analyzer.agent.gigachat.certificate.api-url=https://api.example/v1",
                        "strategy-launches-analyzer.agent.gigachat.certificate.key-store-path=missing-key-store.p12",
                        "strategy-launches-analyzer.agent.gigachat.certificate.key-store-password=" + STORE_PASSWORD,
                        "strategy-launches-analyzer.agent.gigachat.certificate.trust-store-path=missing-trust-store.p12",
                        "strategy-launches-analyzer.agent.gigachat.certificate.trust-store-password=" + STORE_PASSWORD
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "gigachat.certificate.key-store-path must point to a readable PKCS12 file."
                        ));
    }

    @Test
    void failsFastWhenCertificateStoresAreNotSeparate(@TempDir Path tempDir) throws Exception {
        Path store = pkcs12(tempDir.resolve("shared-store.p12"));

        contextRunner
                .withPropertyValues(
                        "strategy-launches-analyzer.agent.gigachat.auth-mode=certificate",
                        "strategy-launches-analyzer.agent.gigachat.certificate.api-url=https://api.example/v1",
                        "strategy-launches-analyzer.agent.gigachat.certificate.key-store-path=" + store,
                        "strategy-launches-analyzer.agent.gigachat.certificate.key-store-password=" + STORE_PASSWORD,
                        "strategy-launches-analyzer.agent.gigachat.certificate.trust-store-path=" + store,
                        "strategy-launches-analyzer.agent.gigachat.certificate.trust-store-password=" + STORE_PASSWORD
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "gigachat.certificate key-store-path and trust-store-path must reference different files."
                        ));
    }

    @Test
    void failsFastWhenScopeIsUnsupported() {
        String[] properties = userPasswordProperties();
        properties[properties.length - 1] =
                "strategy-launches-analyzer.agent.gigachat.user-password.scope=unsupported";

        contextRunner
                .withPropertyValues(properties)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "gigachat.user-password.scope must be one of: GIGACHAT_API_PERS, "
                                        + "GIGACHAT_API_B2B, GIGACHAT_API_CORP."
                        ));
    }

    @Test
    void failsFastWhenSelectedUrlIsInvalid() {
        String[] properties = userPasswordProperties();
        properties[1] = "strategy-launches-analyzer.agent.gigachat.user-password.api-url=not-a-url";

        contextRunner
                .withPropertyValues(properties)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "gigachat.user-password.api-url must be an absolute HTTP(S) URL."
                        ));
    }

    @Test
    void failsFastWhenAuthModeIsMissing() {
        contextRunner.run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("authMode"));
    }

    @Test
    void failsFastWhenModelIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(GigaChatClientConfiguration.class)
                .withPropertyValues(userPasswordProperties())
                .withPropertyValues(
                        "strategy-launches-analyzer.agent.gigachat.connect-timeout=15s",
                        "strategy-launches-analyzer.agent.gigachat.read-timeout=120s"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("model"));
    }

    private static String[] userPasswordProperties() {
        return new String[]{
                "strategy-launches-analyzer.agent.gigachat.auth-mode=user-password",
                "strategy-launches-analyzer.agent.gigachat.user-password.api-url=https://api.example/v1",
                "strategy-launches-analyzer.agent.gigachat.user-password.auth-api-url=https://auth.example/v1",
                "strategy-launches-analyzer.agent.gigachat.user-password.username=test-user",
                "strategy-launches-analyzer.agent.gigachat.user-password.password=test-password",
                "strategy-launches-analyzer.agent.gigachat.user-password.scope=GIGACHAT_API_PERS"
        };
    }

    private static Path pkcs12(Path path) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, STORE_PASSWORD.toCharArray());
        try (OutputStream output = Files.newOutputStream(path)) {
            store.store(output, STORE_PASSWORD.toCharArray());
        }
        return path.toAbsolutePath();
    }
}

package ru.sberbank.strategy_launches_analyzer.config;

import chat.giga.client.GigaChatClient;
import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder.UserPasswordAuthBuilder;
import chat.giga.http.client.JdkHttpClientBuilder;
import chat.giga.http.client.SSL;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GigaChatProperties.class)
public class GigaChatClientConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            name = "strategy-launches-analyzer.agent.gigachat.auth-mode",
            havingValue = "certificate"
    )
    public GigaChatClient certificateGigaChatClient(GigaChatProperties properties) {
        GigaChatProperties.Certificate certificate = properties.requiredCertificate();
        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout());
        httpClientBuilder.ssl(SSL.builder()
                .verifySslCerts(properties.verifySslCerts())
                .keystorePath(certificate.keyStorePath())
                .keystorePassword(certificate.keyStorePassword())
                .keystoreType("PKCS12")
                .truststorePath(certificate.trustStorePath())
                .truststorePassword(certificate.trustStorePassword())
                .trustStoreType("PKCS12")
                .build());

        return commonBuilder(properties, properties.verifySslCerts())
                .apiUrl(certificate.apiUrl())
                .authClient(AuthClient.builder()
                        .withCertificatesAuth(httpClientBuilder.build())
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            name = "strategy-launches-analyzer.agent.gigachat.auth-mode",
            havingValue = "user-password"
    )
    public GigaChatClient userPasswordGigaChatClient(GigaChatProperties properties) {
        GigaChatProperties.UserPassword credentials = properties.requiredUserPassword();
        UserPasswordAuthBuilder authBuilder = UserPasswordAuthBuilder.builder()
                .user(credentials.username())
                .password(credentials.password())
                .authApiUrl(credentials.authApiUrl())
                .scope(credentials.scopeValue())
                .connectTimeout(properties.connectTimeoutSeconds())
                .readTimeout(properties.readTimeoutSeconds())
                .verifySslCerts(false)
                .build();

        return commonBuilder(properties, false)
                .apiUrl(credentials.apiUrl())
                .authClient(AuthClient.builder()
                        .withUserPassword(authBuilder)
                        .build())
                .build();
    }

    private static chat.giga.client.GigaChatClientImpl.GigaChatClientImplBuilder commonBuilder(
            GigaChatProperties properties,
            boolean verifySslCerts
    ) {
        return GigaChatClient.builder()
                .connectTimeout(properties.connectTimeoutSeconds())
                .readTimeout(properties.readTimeoutSeconds())
                .verifySslCerts(verifySslCerts)
                .maxRetriesOnAuthError(properties.maxRetriesOnAuthError())
                .logRequests(false)
                .logResponses(false);
    }
}

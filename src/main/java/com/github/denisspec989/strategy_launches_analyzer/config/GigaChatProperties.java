package com.github.denisspec989.strategy_launches_analyzer.config;

import chat.giga.model.Scope;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

@Validated
@ConfigurationProperties("strategy-launches-analyzer.agent.gigachat")
public record GigaChatProperties(
        @NotNull GigaChatAuthMode authMode,
        @NotBlank String model,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        boolean verifySslCerts,
        @Min(0) int maxRetriesOnAuthError,
        Certificate certificate,
        UserPassword userPassword
) {
    public GigaChatProperties {
        requirePositiveDuration(connectTimeout, "connect-timeout");
        requirePositiveDuration(readTimeout, "read-timeout");
    }

    public Certificate requiredCertificate() {
        if (certificate == null) {
            throw new IllegalArgumentException("gigachat.certificate configuration is required for certificate auth.");
        }
        certificate.validate();
        return certificate;
    }

    public UserPassword requiredUserPassword() {
        if (userPassword == null) {
            throw new IllegalArgumentException(
                    "gigachat.user-password configuration is required for user-password auth."
            );
        }
        userPassword.validate();
        return userPassword;
    }

    public int connectTimeoutSeconds() {
        return wholeSeconds(connectTimeout, "connect-timeout");
    }

    public int readTimeoutSeconds() {
        return wholeSeconds(readTimeout, "read-timeout");
    }

    public record Certificate(
            String apiUrl,
            String keyStorePath,
            String keyStorePassword,
            String trustStorePath,
            String trustStorePassword
    ) {
        private void validate() {
            requireHttpUrl(apiUrl, "gigachat.certificate.api-url");
            requireReadableFile(keyStorePath, "gigachat.certificate.key-store-path");
            requireText(keyStorePassword, "gigachat.certificate.key-store-password");
            requireReadableFile(trustStorePath, "gigachat.certificate.trust-store-path");
            requireText(trustStorePassword, "gigachat.certificate.trust-store-password");
            if (normalizedPath(keyStorePath).equals(normalizedPath(trustStorePath))) {
                throw new IllegalArgumentException(
                        "gigachat.certificate key-store-path and trust-store-path must reference different files."
                );
            }
        }
    }

    public record UserPassword(
            String apiUrl,
            String authApiUrl,
            String username,
            String password,
            String scope
    ) {
        private void validate() {
            requireHttpUrl(apiUrl, "gigachat.user-password.api-url");
            requireHttpUrl(authApiUrl, "gigachat.user-password.auth-api-url");
            requireText(username, "gigachat.user-password.username");
            requireText(password, "gigachat.user-password.password");
            scopeValue();
        }

        public Scope scopeValue() {
            validateScopeText();
            try {
                return Scope.valueOf(scope.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "gigachat.user-password.scope must be one of: GIGACHAT_API_PERS, "
                                + "GIGACHAT_API_B2B, GIGACHAT_API_CORP.",
                        ex
                );
            }
        }

        private void validateScopeText() {
            requireText(scope, "gigachat.user-password.scope");
        }
    }

    private static void requirePositiveDuration(Duration value, String property) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException("gigachat." + property + " must be positive.");
        }
    }

    private static int wholeSeconds(Duration value, String property) {
        requirePositiveDuration(value, property);
        if (value == null) {
            throw new IllegalArgumentException("gigachat." + property + " is required.");
        }
        if (value.toNanosPart() != 0) {
            throw new IllegalArgumentException("gigachat." + property + " must contain whole seconds.");
        }
        return Math.toIntExact(value.toSeconds());
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " is required.");
        }
    }

    private static void requireReadableFile(String value, String property) {
        requireText(value, property);
        Path path = normalizedPath(value);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(property + " must point to a readable PKCS12 file.");
        }
    }

    private static Path normalizedPath(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static void requireHttpUrl(String value, String property) {
        requireText(value, property);
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException(property + " must be an absolute HTTP(S) URL.");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(property + " must be an absolute HTTP(S) URL.", ex);
        }
    }
}

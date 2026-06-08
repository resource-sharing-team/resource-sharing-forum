package com.resourcesharing.forum;

import com.resourcesharing.forum.config.ProductionDeploymentConfig;
import com.resourcesharing.forum.security.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionDeploymentConfigTest {
    private final ProductionDeploymentConfig config = new ProductionDeploymentConfig();
    private static final String STRONG_JWT = "strong-production-secret-32-chars-minimum";
    private static final String STRONG_DB_PASSWORD = "strong-db-password-2026";
    private static final String VALID_CORS = "http://localhost:5173,https://forum.example.com";

    @Test
    void prodJwtGuardRejectsDevelopmentSecret() {
        JwtProperties properties = new JwtProperties("resource-sharing-forum-dev-secret-must-be-changed-2026", 120);

        assertThatThrownBy(() -> guard(properties, STRONG_DB_PASSWORD, VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be replaced");
    }

    @Test
    void prodJwtGuardRejectsPlaceholderSecret() {
        JwtProperties properties = new JwtProperties("change-this-app-secret-please-change-this", 120);

        assertThatThrownBy(() -> guard(properties, STRONG_DB_PASSWORD, VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be replaced");
    }

    @Test
    void prodJwtGuardRejectsShortSecret() {
        JwtProperties properties = new JwtProperties("short-secret", 120);

        assertThatThrownBy(() -> guard(properties, STRONG_DB_PASSWORD, VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void prodGuardRejectsPlaceholderDatabasePassword() {
        JwtProperties properties = new JwtProperties(STRONG_JWT, 120);

        assertThatThrownBy(() -> guard(properties, "change-this-app-password", VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD must be replaced");
    }

    @Test
    void prodGuardRejectsShortDatabasePassword() {
        JwtProperties properties = new JwtProperties(STRONG_JWT, 120);

        assertThatThrownBy(() -> guard(properties, "short", VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD must contain at least 12 characters");
    }

    @Test
    void prodGuardRejectsWildcardCorsOrigins() {
        JwtProperties properties = new JwtProperties(STRONG_JWT, 120);

        assertThatThrownBy(() -> guard(properties, STRONG_DB_PASSWORD, "*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not use wildcard");
    }

    @Test
    void prodGuardRejectsNonHttpCorsOrigins() {
        JwtProperties properties = new JwtProperties(STRONG_JWT, 120);

        assertThatThrownBy(() -> guard(properties, STRONG_DB_PASSWORD, "localhost:5173"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP(S) origins");
    }

    @Test
    void prodGuardAcceptsStrongSecretsAndExplicitCorsOrigins() {
        JwtProperties properties = new JwtProperties(STRONG_JWT, 120);

        assertThat(guard(properties, STRONG_DB_PASSWORD, VALID_CORS)).isNotNull();
    }

    private Object guard(JwtProperties properties, String dbPassword, String corsAllowedOrigins) {
        return config.productionDeploymentGuard(properties, dbPassword, corsAllowedOrigins);
    }
}

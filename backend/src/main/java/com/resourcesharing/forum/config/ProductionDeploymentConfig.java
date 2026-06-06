package com.resourcesharing.forum.config;

import com.resourcesharing.forum.security.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
public class ProductionDeploymentConfig {
    private static final String DEV_JWT_SECRET = "resource-sharing-forum-dev-secret-must-be-changed-2026";
    private static final int MIN_JWT_SECRET_LENGTH = 32;
    private static final int MIN_DB_PASSWORD_LENGTH = 12;

    @Bean
    public Object productionDeploymentGuard(
            JwtProperties jwtProperties,
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${forum.cors.allowed-origins:}") String corsAllowedOrigins) {
        String secret = jwtProperties.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required when running with the prod profile");
        }
        if (DEV_JWT_SECRET.equals(secret) || containsPlaceholder(secret)) {
            throw new IllegalStateException("JWT_SECRET must be replaced before production deployment");
        }
        if (secret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters");
        }

        validateDatabasePassword(dbPassword);
        validateCorsAllowedOrigins(corsAllowedOrigins);
        return new Object();
    }

    private static void validateDatabasePassword(String dbPassword) {
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD is required when running with the prod profile");
        }
        String normalized = dbPassword.trim().toLowerCase();
        if (containsPlaceholder(dbPassword) || normalized.equals("root") || normalized.equals("admin")
                || normalized.equals("password") || normalized.equals("123456")) {
            throw new IllegalStateException("DB_PASSWORD must be replaced before production deployment");
        }
        if (dbPassword.length() < MIN_DB_PASSWORD_LENGTH) {
            throw new IllegalStateException("DB_PASSWORD must contain at least 12 characters");
        }
    }

    private static void validateCorsAllowedOrigins(String corsAllowedOrigins) {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS is required when running with the prod profile");
        }
        String[] origins = corsAllowedOrigins.split(",");
        boolean hasOrigin = false;
        for (String origin : origins) {
            String value = origin.trim();
            if (value.isEmpty()) {
                continue;
            }
            hasOrigin = true;
            if ("*".equals(value)) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must not use wildcard origins");
            }
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain HTTP(S) origins");
            }
        }
        if (!hasOrigin) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS is required when running with the prod profile");
        }
    }

    private static boolean containsPlaceholder(String value) {
        String normalized = value.toLowerCase();
        return normalized.contains("change-this") || normalized.contains("replace-with");
    }
}

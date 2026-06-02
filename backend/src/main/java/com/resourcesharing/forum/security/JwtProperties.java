package com.resourcesharing.forum.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forum.jwt")
public record JwtProperties(String secret, long expiresMinutes) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = "resource-sharing-forum-dev-secret-must-be-changed-2026";
        }
        if (expiresMinutes <= 0) {
            expiresMinutes = 120;
        }
    }
}


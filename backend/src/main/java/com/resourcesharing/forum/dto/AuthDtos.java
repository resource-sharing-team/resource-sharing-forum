package com.resourcesharing.forum.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String account, @NotBlank String password, boolean rememberMe) {
    }

    public record RegisterRequest(@NotBlank String username, @NotBlank String email, @NotBlank String password) {
    }

    public record AuthResult(Long userId, String nickname, String role, String token, Instant expireAt) {
    }
}


package com.resourcesharing.forum.service;

import com.resourcesharing.forum.dto.AuthDtos.AuthResult;
import com.resourcesharing.forum.dto.AuthDtos.LoginRequest;
import com.resourcesharing.forum.dto.AuthDtos.RegisterRequest;
import com.resourcesharing.forum.security.JwtProperties;
import com.resourcesharing.forum.security.JwtService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(JwtService jwtService, JwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    public AuthResult login(LoginRequest request) {
        String token = jwtService.generate("1", "USER");
        return new AuthResult(1L, request.account(), "USER", token, Instant.now().plusSeconds(jwtProperties.expiresMinutes() * 60));
    }

    public AuthResult register(RegisterRequest request) {
        String token = jwtService.generate("1", "USER");
        return new AuthResult(1L, request.username(), "USER", token, Instant.now().plusSeconds(jwtProperties.expiresMinutes() * 60));
    }
}


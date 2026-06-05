package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.service.LegacyDesignSpecForumService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("designSpecAuthService")
public class AuthService {
    private final LegacyDesignSpecForumService legacy;

    public AuthService(LegacyDesignSpecForumService legacy) {
        this.legacy = legacy;
    }

    public Map<String, Object> login(Map<String, Object> request) {
        return legacy.login(request);
    }

    public Map<String, Object> register(Map<String, Object> request) {
        return legacy.register(request);
    }

    public Map<String, Object> requestResetPasswordCode(Map<String, Object> request) {
        return legacy.requestResetPasswordCode(request);
    }

    public Map<String, Object> resetPassword(Map<String, Object> request) {
        return legacy.resetPassword(request);
    }
}

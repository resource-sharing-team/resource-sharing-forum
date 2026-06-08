package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {
    private final DesignSpecForumService forumService;

    public AuthController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        return ApiResponse.created(forumService.register(request));
    }

    @PostMapping("/register/code")
    public ApiResponse<Map<String, Object>> registerCode(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(forumService.requestRegisterCode(request));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(forumService.login(request));
    }

    @PostMapping("/reset-password/code")
    public ApiResponse<Map<String, Object>> resetPasswordCode(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(forumService.requestResetPasswordCode(request));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(forumService.resetPassword(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.success(Map.of("ok", true));
    }

}

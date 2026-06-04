package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/me", "/api/v1/user/profile"})
public class MeController {
    private final DesignSpecForumService forumService;

    public MeController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> current(Authentication authentication) {
        return ApiResponse.success(forumService.userProfile(accountId(authentication)));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.updateUserProfile(accountId(authentication), request));
    }

    @PostMapping("/password")
    public ApiResponse<Map<String, Object>> password(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.changePassword(accountId(authentication), request));
    }

    @PostMapping("/email")
    public ApiResponse<Map<String, Object>> email(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.changeEmail(accountId(authentication), request));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(Authentication authentication) {
        return ApiResponse.success(Map.of("profile", forumService.userProfile(accountId(authentication))));
    }

    private static Long accountId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return 1L;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }
}

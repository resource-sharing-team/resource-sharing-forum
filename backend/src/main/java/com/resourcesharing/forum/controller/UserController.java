package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final DesignSpecForumService forumService;

    public UserController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser(Authentication authentication) {
        return ApiResponse.success(forumService.userProfile(accountId(authentication)));
    }

    private static Long accountId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}


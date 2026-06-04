package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/appeals", "/api/v1/appeals"})
public class AppealController {
    private final DesignSpecForumService forumService;

    public AppealController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> appeal(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.created(forumService.appeal(accountId(authentication), request));
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

package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/reports", "/api/v1/reports"})
public class ReportController {
    private final DesignSpecForumService forumService;

    public ReportController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> report(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.created(forumService.report(accountId(authentication), request));
    }

    @PostMapping("/copyright-complaints")
    public ApiResponse<Map<String, Object>> copyrightComplaint(@RequestBody Map<String, Object> request, Authentication authentication) {
        request.put("target", "COPYRIGHT");
        request.put("type", "COPYRIGHT");
        return ApiResponse.created(forumService.report(accountId(authentication), request));
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


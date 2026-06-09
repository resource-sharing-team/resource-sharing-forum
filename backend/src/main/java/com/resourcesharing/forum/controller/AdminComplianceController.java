package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/admin", "/api/v1/admin"})
public class AdminComplianceController {
    private final DesignSpecForumService forumService;

    public AdminComplianceController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @PostMapping("/reports/{id}/handle")
    public ApiResponse<Map<String, Object>> handleReport(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.handleReport(id, accountId(authentication), request));
    }

    @PostMapping("/appeals/{id}/handle")
    public ApiResponse<Map<String, Object>> handleAppeal(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.handleAppeal(id, accountId(authentication), request));
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<Map<String, Object>>> logs(@RequestParam Map<String, String> params) {
        return ApiResponse.success(forumService.adminLogs(params));
    }

    @PutMapping("/members/{id}/disable")
    public ApiResponse<Map<String, Object>> disableMember(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            Authentication authentication
    ) {
        return ApiResponse.success(forumService.disableMember(accountId(authentication), id, request));
    }

    @PutMapping("/members/{id}/enable")
    public ApiResponse<Map<String, Object>> enableMember(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.enableMember(accountId(authentication), id));
    }

    @GetMapping("/categories")
    public ApiResponse<PageResult<Map<String, Object>>> categories(@RequestParam Map<String, String> params) {
        return ApiResponse.success(forumService.listCategories(params));
    }

    @PostMapping("/categories")
    public ApiResponse<Map<String, Object>> createCategory(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.created(forumService.createCategory(accountId(authentication), request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<Map<String, Object>> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.updateCategory(accountId(authentication), id, request));
    }

    @PutMapping("/categories/{id}/disable")
    public ApiResponse<Map<String, Object>> disableCategory(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.disableCategory(accountId(authentication), id));
    }

    @GetMapping("/tags")
    public ApiResponse<PageResult<Map<String, Object>>> tags(@RequestParam Map<String, String> params) {
        return ApiResponse.success(forumService.listTags(params));
    }

    @PostMapping("/tags")
    public ApiResponse<Map<String, Object>> createTag(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.created(forumService.createTag(accountId(authentication), request));
    }

    @PostMapping("/tags/backfill")
    public ApiResponse<Map<String, Object>> backfillNormativeTags(Authentication authentication) {
        return ApiResponse.success(forumService.backfillNormativeTags(accountId(authentication)));
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<Map<String, Object>> updateTag(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.updateTag(accountId(authentication), id, request));
    }

    @PutMapping("/tags/{id}/disable")
    public ApiResponse<Map<String, Object>> disableTag(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.disableTag(accountId(authentication), id));
    }

    @PostMapping("/tags/merge")
    public ApiResponse<Map<String, Object>> mergeTags(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.mergeTags(accountId(authentication), request));
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        return ApiResponse.success(forumService.systemConfig());
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.updateSystemConfig(accountId(authentication), request));
    }

    @PostMapping("/cache/refresh")
    public ApiResponse<Map<String, Object>> refreshCache(Authentication authentication) {
        return ApiResponse.success(forumService.refreshCache(accountId(authentication)));
    }

    @PostMapping("/requests/{id}/close")
    public ApiResponse<Map<String, Object>> closeRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            Authentication authentication
    ) {
        return ApiResponse.success(forumService.closeRequestByAdmin(accountId(authentication), id, request));
    }

    @DeleteMapping("/replies/{id}")
    public ApiResponse<Map<String, Object>> deleteReply(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.deleteReplyByAdmin(accountId(authentication), id));
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

package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.system.AdminDashboardService;
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
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/content")
    public ApiResponse<Map<String, Object>> content(@RequestParam Map<String, String> params) {
        return ApiResponse.success(dashboardService.content(params));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<Map<String, Object>>> users(@RequestParam Map<String, String> params) {
        return ApiResponse.success(dashboardService.users(params));
    }

    @GetMapping("/compliance")
    public ApiResponse<Map<String, Object>> compliance(@RequestParam Map<String, String> params) {
        return ApiResponse.success(dashboardService.compliance(params));
    }

    @GetMapping("/catalog")
    public ApiResponse<Map<String, Object>> catalog(@RequestParam Map<String, String> params) {
        return ApiResponse.success(dashboardService.catalog(params));
    }

    @GetMapping("/catalog/options")
    public ApiResponse<Map<String, Object>> catalogOptions() {
        return ApiResponse.success(dashboardService.catalogOptions());
    }

    @GetMapping("/config/full")
    public ApiResponse<Map<String, Object>> fullConfig() {
        return ApiResponse.success(dashboardService.fullConfig());
    }

    @PutMapping("/config/member-levels/{id}")
    public ApiResponse<Map<String, Object>> updateMemberLevel(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(dashboardService.updateMemberLevel(accountId(authentication), id, request));
    }

    @PostMapping("/comments/{id}/hide")
    public ApiResponse<Map<String, Object>> hideComment(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(dashboardService.hideComment(accountId(authentication), id));
    }

    @PostMapping("/comments/{id}/restore")
    public ApiResponse<Map<String, Object>> restoreComment(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(dashboardService.restoreComment(accountId(authentication), id));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Map<String, Object>> deleteComment(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(dashboardService.deleteComment(accountId(authentication), id));
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

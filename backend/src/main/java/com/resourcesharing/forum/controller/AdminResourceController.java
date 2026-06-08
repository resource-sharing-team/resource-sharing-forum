package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.dto.ResourceDtos.ReviewRequest;
import com.resourcesharing.forum.service.AdminResourceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/admin/resources", "/api/v1/admin/resources"})
public class AdminResourceController {
    private final AdminResourceService adminResourceService;

    public AdminResourceController(AdminResourceService adminResourceService) {
        this.adminResourceService = adminResourceService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(adminResourceService.list(params, accountId(authentication)));
    }

    @GetMapping("/pending")
    public ApiResponse<PageResult<Map<String, Object>>> pending(PageQuery query, Authentication authentication) {
        return ApiResponse.success(adminResourceService.pending(query, accountId(authentication)));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approve(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(adminResourceService.review(new ReviewRequest(id, true, null), accountId(authentication)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            Authentication authentication
    ) {
        String reason = request == null || request.get("reason") == null ? "瀹℃牳椹冲洖" : String.valueOf(request.get("reason"));
        return ApiResponse.success(adminResourceService.review(new ReviewRequest(id, false, reason), accountId(authentication)));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<Map<String, Object>> review(@Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return ApiResponse.success(adminResourceService.review(request, accountId(authentication)));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<Map<String, Object>> offline(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(adminResourceService.transition(id, accountId(authentication), "OFFLINE", request));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(adminResourceService.transition(id, accountId(authentication), "RESTORE", request));
    }

    @PostMapping("/{id}/risk-review")
    public ApiResponse<Map<String, Object>> riskReview(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(adminResourceService.transition(id, accountId(authentication), "RISK_REVIEW", request));
    }

    @PostMapping("/{id}/copyright-down")
    public ApiResponse<Map<String, Object>> copyrightDown(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(adminResourceService.transition(id, accountId(authentication), "COPYRIGHT_DOWN", request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(adminResourceService.transition(id, accountId(authentication), "DELETE", request));
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

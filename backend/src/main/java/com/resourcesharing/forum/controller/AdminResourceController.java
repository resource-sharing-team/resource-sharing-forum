package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.dto.ResourceDtos.ResourceView;
import com.resourcesharing.forum.dto.ResourceDtos.ReviewRequest;
import com.resourcesharing.forum.service.AdminResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/resources")
public class AdminResourceController {
    private final AdminResourceService adminResourceService;

    public AdminResourceController(AdminResourceService adminResourceService) {
        this.adminResourceService = adminResourceService;
    }

    @GetMapping("/pending")
    public ApiResponse<PageResult<ResourceView>> pending(PageQuery query) {
        return ApiResponse.success(adminResourceService.pending(query));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ResourceView> approve(@PathVariable Long id) {
        return ApiResponse.success(adminResourceService.review(new ReviewRequest(id, true, null)));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ResourceView> reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request) {
        String reason = request == null ? "审核驳回" : request.reason();
        return ApiResponse.success(adminResourceService.review(new ReviewRequest(id, false, reason)));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<ResourceView> review(@Valid @RequestBody ReviewRequest request) {
        return ApiResponse.success(adminResourceService.review(request));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable Long id) {
        adminResourceService.offline(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        adminResourceService.restore(id);
        return ApiResponse.success();
    }
}


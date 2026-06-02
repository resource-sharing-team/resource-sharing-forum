package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.dto.ResourceDtos.ResourcePublishRequest;
import com.resourcesharing.forum.dto.ResourceDtos.ResourceView;
import com.resourcesharing.forum.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    public ApiResponse<ResourceView> publish(@Valid @RequestBody ResourcePublishRequest request) {
        return ApiResponse.success(resourceService.publish(request));
    }

    @GetMapping
    public ApiResponse<PageResult<ResourceView>> list(PageQuery query) {
        return ApiResponse.success(resourceService.list(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResourceView> detail(@PathVariable Long id) {
        return ApiResponse.success(resourceService.detail(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ResourceView> submit(@PathVariable Long id) {
        return ApiResponse.success(resourceService.submitAudit(id));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Void> favorite(@PathVariable Long id) {
        resourceService.favorite(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/like")
    public ApiResponse<Void> like(@PathVariable Long id) {
        resourceService.like(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/score")
    public ApiResponse<Void> score(@PathVariable Long id, @RequestParam int score) {
        resourceService.score(id, score);
        return ApiResponse.success();
    }
}


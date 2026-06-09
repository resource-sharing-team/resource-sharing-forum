package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.PublicMetadataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class PublicMetadataController {
    private final PublicMetadataService metadataService;

    public PublicMetadataController(PublicMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping({"/api/categories", "/api/v1/categories"})
    public ApiResponse<List<Map<String, Object>>> categories(@RequestParam(required = false) String status) {
        return ApiResponse.success(metadataService.categories(status));
    }

    @GetMapping({"/api/tags/suggest", "/api/v1/tags/suggest"})
    public ApiResponse<List<Map<String, Object>>> tagSuggest(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(metadataService.suggestTags(keyword, limit));
    }

    @GetMapping({"/api/resource-types", "/api/v1/resource-types"})
    public ApiResponse<List<Map<String, Object>>> resourceTypes() {
        return ApiResponse.success(metadataService.resourceTypes());
    }

    @GetMapping({"/api/announcements", "/api/v1/announcements"})
    public ApiResponse<PageResult<Map<String, Object>>> announcements(@RequestParam Map<String, String> params) {
        return ApiResponse.success(metadataService.announcements(params));
    }
}

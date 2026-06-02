package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.dto.RequestDtos.AnswerCreateRequest;
import com.resourcesharing.forum.dto.RequestDtos.ResourceRequestCreateRequest;
import com.resourcesharing.forum.service.RequestResourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/requests")
public class RequestResourceController {
    private final RequestResourceService requestResourceService;

    public RequestResourceController(RequestResourceService requestResourceService) {
        this.requestResourceService = requestResourceService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody ResourceRequestCreateRequest request) {
        return ApiResponse.success(requestResourceService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(PageQuery query) {
        return ApiResponse.success(requestResourceService.list(query));
    }

    @PostMapping("/{id}/answers")
    public ApiResponse<Map<String, Object>> answer(@PathVariable Long id, @Valid @RequestBody AnswerCreateRequest request) {
        return ApiResponse.success(requestResourceService.answer(id, request));
    }

    @PostMapping("/{id}/answers/{answerId}/accept")
    public ApiResponse<Void> accept(@PathVariable Long id, @PathVariable Long answerId) {
        requestResourceService.accept(id, answerId);
        return ApiResponse.success();
    }
}


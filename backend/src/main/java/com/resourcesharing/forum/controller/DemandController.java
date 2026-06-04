package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demands")
public class DemandController {
    private final DesignSpecForumService forumService;

    public DemandController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam Map<String, String> params) {
        return ApiResponse.success(forumService.listRequests(params));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.requestDetail(id, accountId(authentication)));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.created(forumService.createRequest(accountId(authentication), request));
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<Map<String, Object>> comment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            Authentication authentication
    ) {
        return ApiResponse.created(forumService.addComment("REQUEST_POST", id, value(request, "content"), accountId(authentication)));
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

    private static String value(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}

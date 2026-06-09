package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.DesignSpecForumService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/resources", "/api/v1/resources"})
public class ResourceController {
    private final DesignSpecForumService forumService;

    public ResourceController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> publishMultipart(
            @RequestParam Map<String, String> form,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication
    ) {
        return ApiResponse.created(forumService.publishResource(accountId(authentication), new java.util.LinkedHashMap<>(form), files));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> publishJson(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.created(forumService.publishResource(accountId(authentication), request, null));
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(forumService.listResources(params, accountId(authentication)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.resourceDetail(id, accountId(authentication)));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.submitResource(id, accountId(authentication)));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<Map<String, Object>> withdraw(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            Authentication authentication
    ) {
        return ApiResponse.success(forumService.withdrawResource(id, accountId(authentication), request == null ? Map.of() : request));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Object>> favorite(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.toggleResourceInteraction(id, "favorite", accountId(authentication)));
    }

    @PostMapping("/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.toggleResourceInteraction(id, "like", accountId(authentication)));
    }

    @PostMapping("/{id}/download")
    public ApiResponse<Map<String, Object>> download(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request,
            Authentication authentication
    ) {
        return ApiResponse.success(forumService.downloadResource(id, longValue(request, "attachmentId", null), accountId(authentication)));
    }

    @PostMapping("/{id}/rating")
    public ApiResponse<Map<String, Object>> rating(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            Authentication authentication
    ) {
        return ApiResponse.success(forumService.rateResource(id, accountId(authentication), request));
    }

    @PutMapping("/{id}/audit")
    public ApiResponse<Map<String, Object>> audit(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.auditResource(id, accountId(authentication), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        forumService.deleteResource(id, accountId(authentication));
        return ApiResponse.success();
    }

    @PostMapping("/{id}/comments")
    public ApiResponse<Map<String, Object>> comment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            Authentication authentication
    ) {
        return ApiResponse.created(forumService.addComment("RESOURCE", id, value(request, "content"), accountId(authentication)));
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

    private static Long longValue(Map<String, Object> request, String key, Long fallback) {
        if (request == null || request.get(key) == null) {
            return fallback;
        }
        Object value = request.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String value(Map<String, Object> request, String key) {
        Object value = request == null ? null : request.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}

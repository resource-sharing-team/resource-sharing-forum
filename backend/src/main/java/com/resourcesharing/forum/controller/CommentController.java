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
@RequestMapping({"/api/comments", "/api/v1/comments"})
public class CommentController {
    private final DesignSpecForumService forumService;

    public CommentController(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(forumService.listComments(params, accountId(authentication)));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.created(forumService.addComment(accountId(authentication), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.commentDetail(id, accountId(authentication)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        return ApiResponse.success(forumService.updateComment(id, accountId(authentication), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        forumService.deleteComment(id, accountId(authentication));
        return ApiResponse.success();
    }

    @PostMapping("/{id}/like")
    public ApiResponse<Map<String, Object>> like(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.likeComment(id, accountId(authentication)));
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

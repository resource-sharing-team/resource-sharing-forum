package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/notifications", "/api/v1/notifications"})
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(notificationService.list(accountId(authentication), Math.max(1, page), Math.max(1, Math.min(100, size))));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(Authentication authentication) {
        return ApiResponse.success(Map.of("count", notificationService.unreadCount(accountId(authentication))));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id, Authentication authentication) {
        notificationService.read(accountId(authentication), id);
        return ApiResponse.success();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(Authentication authentication) {
        notificationService.readAll(accountId(authentication));
        return ApiResponse.success();
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


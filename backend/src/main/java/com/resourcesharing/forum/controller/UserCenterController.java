package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.UserCenterService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class UserCenterController {
    private final UserCenterService userCenterService;

    public UserCenterController(UserCenterService userCenterService) {
        this.userCenterService = userCenterService;
    }

    @GetMapping("/resources")
    public ApiResponse<PageResult<Map<String, Object>>> resources(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(userCenterService.resources(accountId(authentication), params));
    }

    @GetMapping("/requests")
    public ApiResponse<PageResult<Map<String, Object>>> requests(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(userCenterService.requests(accountId(authentication), params));
    }

    @GetMapping("/replies")
    public ApiResponse<PageResult<Map<String, Object>>> replies(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(userCenterService.replies(accountId(authentication), params));
    }

    @GetMapping("/favorites")
    public ApiResponse<PageResult<Map<String, Object>>> favorites(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(userCenterService.favorites(accountId(authentication), params));
    }

    @GetMapping("/likes")
    public ApiResponse<PageResult<Map<String, Object>>> likes(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(userCenterService.likes(accountId(authentication), params));
    }

    @GetMapping("/downloads")
    public ApiResponse<PageResult<Map<String, Object>>> downloads(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(userCenterService.downloads(accountId(authentication), params));
    }

    @GetMapping("/login-records")
    public ApiResponse<PageResult<Map<String, Object>>> loginRecords(@RequestParam Map<String, String> params, Authentication authentication) {
        return ApiResponse.success(userCenterService.loginRecords(accountId(authentication), params));
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

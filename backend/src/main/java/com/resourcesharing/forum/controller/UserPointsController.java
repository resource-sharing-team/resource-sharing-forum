package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.MemberService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/points")
public class UserPointsController {
    private final MemberService memberService;

    public UserPointsController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> account(Authentication authentication) {
        return ApiResponse.success(memberService.pointAccount(accountId(authentication)));
    }

    @GetMapping("/flows")
    public ApiResponse<PageResult<Map<String, Object>>> flows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageSize,
            Authentication authentication
    ) {
        int requestedSize = size == null ? (pageSize == null ? 20 : pageSize) : size;
        return ApiResponse.success(memberService.pointFlows(accountId(authentication), page, requestedSize));
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

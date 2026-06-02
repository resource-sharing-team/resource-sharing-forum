package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentMember() {
        return ApiResponse.success(memberService.currentMember());
    }

    @GetMapping("/me/points")
    public ApiResponse<Map<String, Object>> pointFlows() {
        return ApiResponse.success(memberService.pointFlows());
    }
}


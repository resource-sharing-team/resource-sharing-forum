package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser(Authentication authentication) {
        return ApiResponse.success(Map.of(
                "id", authentication.getName(),
                "nickname", "演示用户",
                "role", authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_USER")
        ));
    }
}


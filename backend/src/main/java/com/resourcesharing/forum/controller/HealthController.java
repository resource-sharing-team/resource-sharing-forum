package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public HealthController(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = baseHealthData();
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            data.put("database", "UNCONFIGURED");
            return ResponseEntity.ok(ApiResponse.success(data));
        }

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            data.put("database", "UP");
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (RuntimeException exception) {
            data.put("status", "DOWN");
            data.put("database", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiResponse<>(503, "database unavailable", data, Instant.now().toString()));
        }
    }

    private Map<String, Object> baseHealthData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("time", Instant.now().toString());
        return data;
    }
}

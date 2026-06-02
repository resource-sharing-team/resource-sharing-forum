package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.service.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> report(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(reportService.submitReport(request));
    }

    @PostMapping("/copyright-complaints")
    public ApiResponse<Map<String, Object>> copyrightComplaint(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(reportService.submitCopyrightComplaint(request));
    }
}


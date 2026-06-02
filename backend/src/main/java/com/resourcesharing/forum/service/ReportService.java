package com.resourcesharing.forum.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ReportService {

    public Map<String, Object> submitReport(Map<String, Object> request) {
        return Map.of("id", 1L, "status", "PENDING");
    }

    public Map<String, Object> submitCopyrightComplaint(Map<String, Object> request) {
        return Map.of("id", 1L, "status", "PENDING");
    }
}


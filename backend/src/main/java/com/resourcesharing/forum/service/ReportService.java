package com.resourcesharing.forum.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReportService {
    private final DesignSpecForumService forumService;

    public ReportService(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    public Map<String, Object> submitReport(Map<String, Object> request) {
        return forumService.report(1L, request);
    }

    public Map<String, Object> submitCopyrightComplaint(Map<String, Object> request) {
        Map<String, Object> body = new LinkedHashMap<>(request);
        body.put("type", "COPYRIGHT");
        return forumService.report(1L, body);
    }
}

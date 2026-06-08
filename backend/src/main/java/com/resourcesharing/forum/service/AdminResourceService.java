package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.dto.ResourceDtos.ReviewRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdminResourceService {
    private final DesignSpecForumService forumService;

    public AdminResourceService(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    public PageResult<Map<String, Object>> pending(PageQuery query, Long adminAccountId) {
        return forumService.adminListResources(Map.of(
                "page", String.valueOf(query.page()),
                "size", String.valueOf(query.size()),
                "status", "PENDING_REVIEW"
        ), adminAccountId);
    }

    public Map<String, Object> list(Map<String, String> params, Long adminAccountId) {
        return Map.of("page", forumService.adminListResources(params, adminAccountId));
    }

    public Map<String, Object> review(ReviewRequest request, Long adminAccountId) {
        return forumService.transitionResourceByAdmin(
                request.resourceId(),
                adminAccountId,
                Boolean.TRUE.equals(request.approved()) ? "APPROVE" : "REJECT",
                Map.of("reason", request.reason() == null ? "" : request.reason())
        );
    }

    public Map<String, Object> transition(Long id, Long adminAccountId, String action, Map<String, Object> request) {
        return forumService.transitionResourceByAdmin(id, adminAccountId, action, request == null ? Map.of() : request);
    }
}

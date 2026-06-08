package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.dto.RequestDtos.AnswerCreateRequest;
import com.resourcesharing.forum.dto.RequestDtos.ResourceRequestCreateRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RequestResourceService {
    private final DesignSpecForumService forumService;

    public RequestResourceService(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    public Map<String, Object> create(ResourceRequestCreateRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", request.title());
        body.put("content", request.description());
        body.put("categoryId", request.categoryId());
        body.put("tags", request.tags() == null ? "" : String.join(",", request.tags()));
        body.put("expectedFormat", request.expectedFormat());
        body.put("rewardPoints", request.rewardPoints() == null ? 0 : request.rewardPoints());
        return forumService.createRequest(1L, body);
    }

    public PageResult<Map<String, Object>> list(PageQuery query) {
        return forumService.listRequests(Map.of(
                "page", String.valueOf(query.page()),
                "size", String.valueOf(query.size())
        ));
    }

    public Map<String, Object> answer(Long requestId, AnswerCreateRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", request.content());
        body.put("resourceId", request.resourceId());
        body.put("externalUrl", request.externalUrl());
        return forumService.replyRequest(requestId, 1L, body);
    }

    public void accept(Long requestId, Long answerId) {
        forumService.settleRequest(requestId, 1L, Map.of("replyId", answerId));
    }
}

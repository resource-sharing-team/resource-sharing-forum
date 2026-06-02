package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.dto.RequestDtos.AnswerCreateRequest;
import com.resourcesharing.forum.dto.RequestDtos.ResourceRequestCreateRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RequestResourceService {

    public Map<String, Object> create(ResourceRequestCreateRequest request) {
        return Map.of("id", 1L, "title", request.title(), "status", "OPEN");
    }

    public PageResult<Map<String, Object>> list(PageQuery query) {
        return PageResult.empty(query);
    }

    public Map<String, Object> answer(Long requestId, AnswerCreateRequest request) {
        return Map.of("id", 1L, "requestId", requestId, "status", "NORMAL");
    }

    public void accept(Long requestId, Long answerId) {
    }
}


package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.domain.ResourceStatus;
import com.resourcesharing.forum.dto.ResourceDtos.ResourcePublishRequest;
import com.resourcesharing.forum.dto.ResourceDtos.ResourceView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResourceService {

    public ResourceView publish(ResourcePublishRequest request) {
        return new ResourceView(1L, request.title(), request.type(), ResourceStatus.PENDING_REVIEW,
                request.summary(), request.tags(), 0, 0, null);
    }

    public PageResult<ResourceView> list(PageQuery query) {
        return PageResult.empty(query);
    }

    public ResourceView detail(Long id) {
        return new ResourceView(id, "2026考研政治历年真题完整版", null, ResourceStatus.PUBLISHED,
                "资源详情接口骨架", List.of("考研", "政治", "真题"), 1286, 136, LocalDateTime.now());
    }

    public ResourceView submitAudit(Long id) {
        return new ResourceView(id, "待审核资源", null, ResourceStatus.PENDING_REVIEW,
                "资源已提交审核", List.of(), 0, 0, null);
    }

    public void favorite(Long id) {
    }

    public void like(Long id) {
    }

    public void score(Long id, int score) {
    }
}


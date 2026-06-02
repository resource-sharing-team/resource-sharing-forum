package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.domain.ResourceStatus;
import com.resourcesharing.forum.dto.ResourceDtos.ResourceView;
import com.resourcesharing.forum.dto.ResourceDtos.ReviewRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminResourceService {

    public PageResult<ResourceView> pending(PageQuery query) {
        return PageResult.empty(query);
    }

    public ResourceView review(ReviewRequest request) {
        ResourceStatus status = Boolean.TRUE.equals(request.approved()) ? ResourceStatus.PUBLISHED : ResourceStatus.REJECTED;
        return new ResourceView(request.resourceId(), "审核资源", null, status, request.reason(), List.of(), 0, 0, null);
    }

    public void offline(Long id) {
    }

    public void restore(Long id) {
    }
}


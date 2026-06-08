package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageQuery;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.domain.ResourceStatus;
import com.resourcesharing.forum.domain.ResourceType;
import com.resourcesharing.forum.dto.ResourceDtos.ResourcePublishRequest;
import com.resourcesharing.forum.dto.ResourceDtos.ResourceView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResourceService {
    private final DesignSpecForumService forumService;

    public ResourceService(DesignSpecForumService forumService) {
        this.forumService = forumService;
    }

    public ResourceView publish(ResourcePublishRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", request.title());
        body.put("categoryId", request.categoryId());
        body.put("resourceType", request.type() == null ? "DOCUMENT" : request.type().name());
        body.put("tags", request.tags() == null ? "" : String.join(",", request.tags()));
        body.put("summary", request.summary());
        body.put("description", request.description());
        body.put("externalUrl", request.externalUrl());
        return toView(forumService.publishResource(1L, body, null));
    }

    public PageResult<ResourceView> list(PageQuery query) {
        PageResult<Map<String, Object>> page = forumService.listResources(Map.of(
                "page", String.valueOf(query.page()),
                "size", String.valueOf(query.size())
        ), 1L);
        return new PageResult<>(page.total(), page.list().stream().map(ResourceService::toView).toList(), page.page(), page.size());
    }

    public ResourceView detail(Long id) {
        return toView(forumService.resourceDetail(id, 1L));
    }

    public ResourceView submitAudit(Long id) {
        return toView(forumService.submitResource(id, 1L));
    }

    public void favorite(Long id) {
        forumService.toggleResourceInteraction(id, "favorite", 1L);
    }

    public void like(Long id) {
        forumService.toggleResourceInteraction(id, "like", 1L);
    }

    public void score(Long id, int score) {
        forumService.rateResource(id, 1L, Map.of("score", score));
    }

    @SuppressWarnings("unchecked")
    private static ResourceView toView(Map<String, Object> source) {
        Map<String, Object> resource = source.containsKey("resource") && source.get("resource") instanceof Map<?, ?>
                ? (Map<String, Object>) source.get("resource")
                : source;
        ResourceType type = ResourceType.valueOf(String.valueOf(resource.getOrDefault("resourceType", "DOCUMENT")));
        ResourceStatus status = ResourceStatus.valueOf(String.valueOf(resource.getOrDefault("status", "PENDING_REVIEW")));
        return new ResourceView(
                number(resource.get("id")),
                String.valueOf(resource.getOrDefault("title", "")),
                type,
                status,
                String.valueOf(resource.getOrDefault("summary", "")),
                (List<String>) resource.getOrDefault("tags", List.<String>of()),
                ((Number) resource.getOrDefault("viewCount", 0)).intValue(),
                ((Number) resource.getOrDefault("downloadCount", 0)).intValue(),
                LocalDateTime.now()
        );
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}

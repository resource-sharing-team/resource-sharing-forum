package com.resourcesharing.forum.service.interaction;

import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.LegacyDesignSpecForumService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InteractionService {
    private final LegacyDesignSpecForumService legacy;

    public InteractionService(LegacyDesignSpecForumService legacy) {
        this.legacy = legacy;
    }

    public Map<String, Object> toggleResourceInteraction(Long resourceId, String action, Long accountId) {
        return legacy.toggleResourceInteraction(resourceId, action, accountId);
    }

    public Map<String, Object> rateResource(Long resourceId, Long accountId, Map<String, Object> request) {
        return legacy.rateResource(resourceId, accountId, request);
    }

    public PageResult<Map<String, Object>> listComments(Map<String, String> params, Long accountId) {
        return legacy.listComments(params, accountId);
    }

    public Map<String, Object> addComment(Long accountId, Map<String, Object> request) {
        return legacy.addComment(accountId, request);
    }

    public Map<String, Object> addComment(String targetType, Long targetId, String content, Long accountId) {
        return legacy.addComment(targetType, targetId, content, accountId);
    }

    public Map<String, Object> commentDetail(Long commentId, Long accountId) {
        return legacy.commentDetail(commentId, accountId);
    }

    public Map<String, Object> updateComment(Long commentId, Long accountId, Map<String, Object> request) {
        return legacy.updateComment(commentId, accountId, request);
    }

    public void deleteComment(Long commentId, Long accountId) {
        legacy.deleteComment(commentId, accountId);
    }

    public Map<String, Object> likeComment(Long commentId, Long accountId) {
        return legacy.likeComment(commentId, accountId);
    }
}

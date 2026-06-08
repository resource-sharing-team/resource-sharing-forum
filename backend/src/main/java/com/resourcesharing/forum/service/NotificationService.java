package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageResult;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {
    private final com.resourcesharing.forum.service.notification.NotificationService delegate;

    public NotificationService(com.resourcesharing.forum.service.notification.NotificationService delegate) {
        this.delegate = delegate;
    }

    public PageResult<Map<String, Object>> list(Long accountId, int page, int size) {
        return delegate.list(accountId, page, size);
    }

    public int unreadCount(Long accountId) {
        return delegate.unreadCount(accountId);
    }

    public void read(Long accountId, Long id) {
        delegate.read(accountId, id);
    }

    public void readAll(Long accountId) {
        delegate.readAll(accountId);
    }

    public void createForMember(Long receiverMemberId, String type, String title, String content, String targetType, Long targetId) {
        delegate.createNoticeFromEvent(null, receiverMemberId, type, title, content, targetType, targetId);
    }
}

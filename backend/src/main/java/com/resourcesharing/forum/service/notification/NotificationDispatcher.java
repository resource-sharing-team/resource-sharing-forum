package com.resourcesharing.forum.service.notification;

import com.resourcesharing.forum.service.NotificationService;
import com.resourcesharing.forum.service.support.TxSupport;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {
    private final NotificationService notificationService;
    private final NotificationEventService eventService;
    private final TxSupport txSupport;

    public NotificationDispatcher(NotificationService notificationService, NotificationEventService eventService, TxSupport txSupport) {
        this.notificationService = notificationService;
        this.eventService = eventService;
        this.txSupport = txSupport;
    }

    public void dispatchToMember(Long memberId, String type, String title, String content, String targetType, Long targetId) {
        try {
            txSupport.requiresNew(() -> {
                notificationService.createForMember(memberId, type, title, content, targetType, targetId);
                return null;
            });
        } catch (RuntimeException exception) {
            try {
                txSupport.requiresNew(() -> {
                    eventService.recordFailure(type, targetType, targetId, memberId, title, content, exception.getMessage());
                    return null;
                });
            } catch (RuntimeException ignored) {
                // Notification failures must not roll back the core business transaction.
            }
        }
    }
}

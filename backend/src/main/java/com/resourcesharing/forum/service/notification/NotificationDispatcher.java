package com.resourcesharing.forum.service.notification;

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
        if (memberId == null || memberId == 0) {
            return;
        }
        Long eventId = null;
        try {
            eventId = txSupport.requiresNew(() -> eventService.createPending(type, targetType, targetId, memberId, title, content));
            Long pendingEventId = eventId;
            txSupport.requiresNew(() -> {
                notificationService.createNoticeFromEvent(pendingEventId, memberId, type, title, content, targetType, targetId);
                eventService.markSent(pendingEventId);
                return null;
            });
        } catch (RuntimeException exception) {
            Long failedEventId = eventId;
            try {
                txSupport.requiresNew(() -> {
                    if (failedEventId == null || failedEventId == 0) {
                        eventService.recordFailure(type, targetType, targetId, memberId, title, content, exception.getMessage());
                    } else {
                        eventService.markFailed(failedEventId, exception.getMessage());
                    }
                    return null;
                });
            } catch (RuntimeException ignored) {
                // Notification failures must not roll back the core business transaction.
            }
            try {
                txSupport.requiresNew(() -> {
                    notificationService.createNoticeFromEvent(null, memberId, type, title, content, targetType, targetId);
                    return null;
                });
            } catch (RuntimeException ignored) {
                // The fallback notice is best-effort and must not block the caller.
            }
        }
    }
}

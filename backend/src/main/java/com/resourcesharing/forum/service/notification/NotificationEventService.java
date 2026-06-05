package com.resourcesharing.forum.service.notification;

import com.resourcesharing.forum.service.support.TxSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationEventService {
    private final TxSupport txSupport;

    public NotificationEventService(TxSupport txSupport) {
        this.txSupport = txSupport;
    }

    public void recordFailure(String type, String targetType, Long targetId, Long receiverId, String title, String content, String reason) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO notification_event(event_type, source_type, source_id, receiver_id, payload, status, fail_reason, process_time)
                VALUES (?, ?, ?, ?, JSON_OBJECT('title', ?, 'content', ?), 'FAILED', ?, NOW(3))
                """, type, targetType, targetId, receiverId, title, content, reason);
    }
}

package com.resourcesharing.forum.service.notification;

import com.resourcesharing.forum.service.support.TxSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Service
public class NotificationEventService {
    private final TxSupport txSupport;

    public NotificationEventService(TxSupport txSupport) {
        this.txSupport = txSupport;
    }

    public Long createPending(String type, String targetType, Long targetId, Long receiverId, String title, String content) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null || receiverId == null || receiverId == 0) {
            return null;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO notification_event(event_type, source_type, source_id, receiver_id, payload, status)
                    VALUES (?, ?, ?, ?, JSON_OBJECT('title', ?, 'content', ?), 'PENDING')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, type);
            statement.setString(2, targetType == null ? "SYSTEM" : targetType);
            statement.setLong(3, targetId == null ? 0L : targetId);
            statement.setLong(4, receiverId);
            statement.setString(5, title);
            statement.setString(6, content);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void markSent(Long eventId) {
        if (eventId == null || eventId == 0) {
            return;
        }
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                UPDATE notification_event
                SET status = 'SENT', fail_reason = NULL, process_time = NOW(3)
                WHERE id = ?
                """, eventId);
    }

    public void markFailed(Long eventId, String reason) {
        if (eventId == null || eventId == 0) {
            return;
        }
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                UPDATE notification_event
                SET status = 'FAILED', fail_reason = ?, process_time = NOW(3)
                WHERE id = ?
                """, truncate(reason), eventId);
    }

    public void recordFailure(String type, String targetType, Long targetId, Long receiverId, String title, String content, String reason) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO notification_event(event_type, source_type, source_id, receiver_id, payload, status, fail_reason, process_time)
                VALUES (?, ?, ?, ?, JSON_OBJECT('title', ?, 'content', ?), 'FAILED', ?, NOW(3))
                """, type, targetType == null ? "SYSTEM" : targetType, targetId == null ? 0L : targetId, receiverId, title, content, truncate(reason));
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }
}

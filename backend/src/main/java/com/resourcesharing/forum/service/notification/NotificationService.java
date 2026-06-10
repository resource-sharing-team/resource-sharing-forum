package com.resourcesharing.forum.service.notification;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service("designSpecNotificationService")
public class NotificationService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;

    public NotificationService(TxSupport txSupport, ValueSupport values, ForumLookupService lookup) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
    }

    public PageResult<Map<String, Object>> list(Long accountId, int page, int size) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        Long memberId = lookup.requireMemberId(accountId);
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM system_notice
                WHERE receiver_id = ? AND deleted_at IS NULL
                """, Long.class, memberId);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, notice_type, title, content, target_type, target_id, is_read, read_time, created_at
                FROM system_notice
                WHERE receiver_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "type", rs.getString("notice_type"),
                "title", rs.getString("title"),
                "content", rs.getString("content"),
                "targetType", rs.getString("target_type") == null ? "" : rs.getString("target_type"),
                "targetId", rs.getObject("target_id") == null ? 0L : rs.getLong("target_id"),
                "read", rs.getInt("is_read") == 1,
                "readTime", rs.getObject("read_time") == null ? "" : String.valueOf(rs.getObject("read_time", LocalDateTime.class)),
                "createTime", String.valueOf(rs.getObject("created_at", LocalDateTime.class))
        ), memberId, (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    public int unreadCount(Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return 0;
        }
        Long memberId = lookup.requireMemberId(accountId);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM system_notice
                WHERE receiver_id = ? AND is_read = 0 AND deleted_at IS NULL
                """, Integer.class, memberId);
        return count == null ? 0 : count;
    }

    public void read(Long accountId, Long id) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        Long memberId = lookup.requireMemberId(accountId);
        int updated = jdbc.update("""
                UPDATE system_notice
                SET is_read = 1, read_time = COALESCE(read_time, NOW(3))
                WHERE id = ? AND receiver_id = ? AND deleted_at IS NULL
                """, id, memberId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification does not exist");
        }
    }

    public void readAll(Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        Long memberId = lookup.requireMemberId(accountId);
        jdbc.update("""
                UPDATE system_notice
                SET is_read = 1, read_time = COALESCE(read_time, NOW(3))
                WHERE receiver_id = ? AND is_read = 0 AND deleted_at IS NULL
                """, memberId);
    }

    public void createNoticeFromEvent(Long eventId, Long receiverMemberId, String type, String title, String content, String targetType, Long targetId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null || receiverMemberId == null || receiverMemberId == 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO system_notice(event_id, receiver_id, notice_type, title, content, target_type, target_id, is_read)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, eventId, receiverMemberId, type, title, content, targetType, targetId);
    }

}

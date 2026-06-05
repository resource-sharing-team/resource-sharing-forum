package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class NotificationService {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    public NotificationService(ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbcProvider = jdbcProvider;
    }

    public PageResult<Map<String, Object>> list(Long accountId, int page, int size) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return new PageResult<>(0, java.util.List.of(), page, size);
        }
        Long memberId = requireMemberId(accountId);
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM system_notice
                WHERE receiver_id = ? AND deleted_at IS NULL
                """, Long.class, memberId);
        var list = jdbc.query("""
                SELECT id, notice_type, title, content, target_type, target_id, is_read, read_time, create_time
                FROM system_notice
                WHERE receiver_id = ? AND deleted_at IS NULL
                ORDER BY create_time DESC, id DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> Map.<String, Object>of(
                "id", rs.getLong("id"),
                "type", rs.getString("notice_type"),
                "title", rs.getString("title"),
                "content", rs.getString("content"),
                "targetType", rs.getString("target_type") == null ? "" : rs.getString("target_type"),
                "targetId", rs.getObject("target_id") == null ? 0L : rs.getLong("target_id"),
                "read", rs.getInt("is_read") == 1,
                "readTime", rs.getObject("read_time") == null ? "" : String.valueOf(rs.getObject("read_time", LocalDateTime.class)),
                "createTime", String.valueOf(rs.getObject("create_time", LocalDateTime.class))
        ), memberId, (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    public int unreadCount(Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return 0;
        }
        Long memberId = requireMemberId(accountId);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM system_notice
                WHERE receiver_id = ? AND is_read = 0 AND deleted_at IS NULL
                """, Integer.class, memberId);
        return count == null ? 0 : count;
    }

    public void read(Long accountId, Long id) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        Long memberId = requireMemberId(accountId);
        int updated = jdbc.update("""
                UPDATE system_notice
                SET is_read = 1, read_time = COALESCE(read_time, NOW(3))
                WHERE id = ? AND receiver_id = ? AND deleted_at IS NULL
                """, id, memberId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
    }

    public void readAll(Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        Long memberId = requireMemberId(accountId);
        jdbc.update("""
                UPDATE system_notice
                SET is_read = 1, read_time = COALESCE(read_time, NOW(3))
                WHERE receiver_id = ? AND is_read = 0 AND deleted_at IS NULL
                """, memberId);
    }

    @Transactional
    public void createForMember(Long receiverMemberId, String type, String title, String content, String targetType, Long targetId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        if (receiverMemberId == null || receiverMemberId == 0) {
            return;
        }
        KeyHolder eventKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO notification_event(event_type, source_type, source_id, receiver_id, payload, status, process_time)
                    VALUES (?, ?, ?, ?, JSON_OBJECT('title', ?, 'content', ?), 'SENT', NOW(3))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, type);
            statement.setString(2, targetType == null ? "SYSTEM" : targetType);
            statement.setLong(3, targetId == null ? 0L : targetId);
            statement.setLong(4, receiverMemberId);
            statement.setString(5, title);
            statement.setString(6, content);
            return statement;
        }, eventKey);
        Long eventId = eventKey.getKey() == null ? null : eventKey.getKey().longValue();
        jdbc.update("""
                INSERT INTO system_notice(event_id, receiver_id, notice_type, title, content, target_type, target_id, is_read)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, eventId, receiverMemberId, type, title, content, targetType, targetId);
    }

    private Long requireMemberId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后再查看通知");
        }
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return 1L;
        }
        try {
            return jdbc.queryForObject("""
                    SELECT mp.id
                    FROM member_profile mp
                    JOIN user_account ua ON ua.id = mp.account_id
                    WHERE mp.account_id = ? AND mp.deleted_at IS NULL
                      AND ua.status = 'NORMAL' AND ua.deleted_at IS NULL
                    """, Long.class, accountId);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不是正常会员或不存在");
        }
    }

    private JdbcTemplate jdbc() {
        return jdbcProvider.getIfAvailable();
    }
}

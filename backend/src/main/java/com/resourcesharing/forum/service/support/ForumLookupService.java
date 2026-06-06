package com.resourcesharing.forum.service.support;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ForumLookupService {
    private static final long DEFAULT_MEMBER_ID = 1L;

    private final TxSupport txSupport;

    public ForumLookupService(TxSupport txSupport) {
        this.txSupport = txSupport;
    }

    public Long memberId(Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (accountId == null) {
            return null;
        }
        if (jdbc == null) {
            return DEFAULT_MEMBER_ID;
        }
        try {
            return jdbc.queryForObject("""
                    SELECT mp.id
                    FROM member_profile mp
                    JOIN user_account ua ON ua.id = mp.account_id
                    WHERE mp.account_id = ? AND mp.deleted_at IS NULL
                      AND ua.status = 'NORMAL' AND ua.deleted_at IS NULL
                      AND (ua.locked_until IS NULL OR ua.locked_until <= NOW(3))
                    """, Long.class, accountId);
        } catch (DataAccessException ignored) {
            return null;
        }
    }

    public Long requireMemberId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后再操作");
        }
        Long memberId = memberId(accountId);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在、已禁用或不是会员账号");
        }
        return memberId;
    }

    public Long adminProfileId(Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录管理员账号");
        }
        if (jdbc == null) {
            return 1L;
        }
        try {
            return jdbc.queryForObject("""
                    SELECT ap.id
                    FROM administrator_profile ap
                    JOIN user_account ua ON ua.id = ap.account_id
                    WHERE ap.account_id = ? AND ap.deleted_at IS NULL
                      AND ua.status = 'NORMAL' AND ua.deleted_at IS NULL
                      AND (ua.locked_until IS NULL OR ua.locked_until <= NOW(3))
                      AND ua.role IN ('ADMIN', 'SUPER_ADMIN', 'AUDITOR')
                    """, Long.class, accountId);
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
    }

    public boolean interactionActive(Long memberId, String targetType, Long targetId, String actionType) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null || memberId == null) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM user_interaction
                    WHERE member_id = ? AND target_type = ? AND target_id = ? AND action_type = ? AND status = 'ACTIVE'
                    """, Integer.class, memberId, targetType, targetId, actionType);
            return count != null && count > 0;
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    public int userRating(Long memberId, Long resourceId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null || memberId == null) {
            return 0;
        }
        try {
            Integer score = jdbc.queryForObject("SELECT score FROM resource_rating WHERE member_id = ? AND resource_id = ?", Integer.class, memberId, resourceId);
            return score == null ? 0 : score;
        } catch (DataAccessException ignored) {
            return 0;
        }
    }

    public List<Map<String, Object>> attachments(Long resourceId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return List.of();
        }
        try {
            return jdbc.query("""
                    SELECT id, original_file_name, file_ext, file_size, download_count
                    FROM file_attachment
                    WHERE owner_type = 'RESOURCE' AND owner_id = ? AND status = 'NORMAL' AND deleted_at IS NULL
                    ORDER BY id
                    """, (rs, rowNum) -> Map.of(
                    "id", rs.getLong("id"),
                    "name", rs.getString("original_file_name"),
                    "fileName", rs.getString("original_file_name"),
                    "size", readableSize(rs.getLong("file_size")),
                    "fileSize", rs.getLong("file_size"),
                    "type", firstNonBlank(rs.getString("file_ext"), "file").toUpperCase(),
                    "downloads", rs.getInt("download_count")
            ), resourceId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    public List<String> resourceTags(Long resourceId) {
        return tags("""
                SELECT ti.tag_name
                FROM resource_tag_rel rtr
                JOIN tag_info ti ON ti.id = rtr.tag_id
                WHERE rtr.resource_id = ?
                ORDER BY ti.id
                """, resourceId);
    }

    public List<String> requestTags(Long requestId) {
        return tags("""
                SELECT ti.tag_name
                FROM request_tag_rel rtr
                JOIN tag_info ti ON ti.id = rtr.tag_id
                WHERE rtr.request_id = ?
                ORDER BY ti.id
                """, requestId);
    }

    private List<String> tags(String sql, Long id) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return List.of();
        }
        try {
            return jdbc.queryForList(sql, String.class, id);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String readableSize(long bytes) {
        if (bytes <= 0) {
            return "pending";
        }
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + " KB";
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}

package com.resourcesharing.forum.service.interaction;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.resource.ResourceQueryService;
import com.resourcesharing.forum.service.support.ContentModerationService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class InteractionService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;
    private final ForumLookupService lookup;
    private final ResourceQueryService resourceQueryService;
    private final NotificationDispatcher notificationDispatcher;
    private final ContentModerationService contentModerationService;

    public InteractionService(
            TxSupport txSupport,
            ValueSupport values,
            MappingSupport mappings,
            ForumLookupService lookup,
            ResourceQueryService resourceQueryService,
            NotificationDispatcher notificationDispatcher,
            ContentModerationService contentModerationService
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.lookup = lookup;
        this.resourceQueryService = resourceQueryService;
        this.notificationDispatcher = notificationDispatcher;
        this.contentModerationService = contentModerationService;
    }

    public Map<String, Object> toggleResourceInteraction(Long resourceId, String action, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return defaultResource();
        }
        Long memberId = lookup.requireMemberId(accountId);
        String actionType = "favorite".equalsIgnoreCase(action) ? "FAVORITE" : "LIKE";
        String column = "FAVORITE".equals(actionType) ? "favorite_count" : "like_count";
        txSupport.required(() -> {
            jdbc.update("""
                    INSERT INTO user_interaction(member_id, target_type, target_id, action_type, status)
                    VALUES (?, 'RESOURCE', ?, ?, 'ACTIVE')
                    ON DUPLICATE KEY UPDATE status = IF(status = 'ACTIVE', 'CANCELLED', 'ACTIVE'), updated_at = NOW(3)
                    """, memberId, resourceId, actionType);
            jdbc.update("""
                    UPDATE resource_info r
                    SET %s = (
                        SELECT COUNT(*)
                        FROM user_interaction ui
                        WHERE ui.target_type = 'RESOURCE'
                          AND ui.action_type = ?
                          AND ui.target_id = r.id
                          AND ui.status = 'ACTIVE'
                    )
                    WHERE r.id = ?
                    """.formatted(column), actionType, resourceId);
            return null;
        });
        return resourceQueryService.resource(resourceId, accountId);
    }

    public Map<String, Object> rateResource(Long resourceId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int score = Math.max(1, Math.min(5, (int) values.number(values.firstPresent(request, "score"), 5L).longValue()));
        if (jdbc == null) {
            Map<String, Object> resource = defaultResource();
            resource.put("userRating", score);
            return resource;
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Integer downloaded = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM download_record
                    WHERE member_id = ? AND resource_id = ? AND status = 'SUCCESS'
                    """, Integer.class, memberId, resourceId);
            if (downloaded == null || downloaded == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "resource can be rated only after successful download");
            }
            try {
                jdbc.update("""
                        INSERT INTO resource_rating(member_id, resource_id, score)
                        VALUES (?, ?, ?)
                        """, memberId, resourceId, score);
            } catch (DataAccessException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "resource has already been rated");
            }
            jdbc.update("""
                    UPDATE resource_info r
                    SET rating_count = (SELECT COUNT(*) FROM resource_rating rr WHERE rr.resource_id = r.id),
                        average_rating = COALESCE((SELECT ROUND(AVG(rr.score), 2) FROM resource_rating rr WHERE rr.resource_id = r.id), 0)
                    WHERE r.id = ?
                    """, resourceId);
            return resourceQueryService.resource(resourceId, accountId);
        });
    }

    public PageResult<Map<String, Object>> listComments(Map<String, String> params, Long accountId) {
        String targetType = values.firstNonBlank(params == null ? null : params.get("targetType"), "RESOURCE");
        Long targetId = values.longValue(values.firstNonBlank(params == null ? null : params.get("targetId"), "1"), 1L);
        return comments(targetType, targetId, accountId, values.page(params), values.size(params));
    }

    public Map<String, Object> addComment(Long accountId, Map<String, Object> request) {
        return addComment(
                values.firstNonBlank(values.value(request, "targetType", ""), "RESOURCE"),
                values.number(values.firstPresent(request, "targetId"), 1L),
                values.value(request, "content", ""),
                accountId,
                values.number(values.firstPresent(request, "parentId"), 0L),
                values.number(values.firstPresent(request, "toMemberId"), 0L)
        );
    }

    public Map<String, Object> addComment(String targetType, Long targetId, String content, Long accountId) {
        return addComment(targetType, targetId, content, accountId, 0L, 0L);
    }

    public Map<String, Object> commentDetail(Long commentId, Long accountId) {
        return comment(commentId, accountId);
    }

    public Map<String, Object> updateComment(Long commentId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", commentId, "content", values.value(request, "content", ""), "mine", true);
        }
        String content = normalizeContent(values.value(request, "content", ""));
        int updated = jdbc.update("""
                UPDATE comment_info
                SET content = ?
                WHERE id = ? AND member_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """, content, commentId, lookup.requireMemberId(accountId));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "comment does not exist or cannot be edited");
        }
        return comment(commentId, accountId);
    }

    public void deleteComment(Long commentId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Map<String, Object> row;
            try {
                row = jdbc.queryForObject("""
                        SELECT target_type, target_id
                        FROM comment_info
                        WHERE id = ? AND member_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                        FOR UPDATE
                        """, (rs, rowNum) -> values.map(
                        "targetType", rs.getString("target_type"),
                        "targetId", rs.getLong("target_id")
                ), commentId, memberId);
            } catch (DataAccessException ignored) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "comment does not exist or cannot be deleted");
            }
            jdbc.update("""
                    UPDATE comment_info
                    SET status = 'DELETED', deleted_at = NOW(3)
                    WHERE id = ? AND member_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                    """, commentId, memberId);
            decrementCommentCount(jdbc, String.valueOf(row.get("targetType")), values.number(row.get("targetId"), 0L));
            return null;
        });
    }

    public Map<String, Object> likeComment(Long commentId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", commentId, "liked", true, "likeCount", 1);
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            ensureActiveComment(jdbc, commentId);
            jdbc.update("""
                    INSERT INTO user_interaction(member_id, target_type, target_id, action_type, status)
                    VALUES (?, 'COMMENT', ?, 'LIKE', 'ACTIVE')
                    ON DUPLICATE KEY UPDATE status = IF(status = 'ACTIVE', 'CANCELLED', 'ACTIVE'), updated_at = NOW(3)
                    """, memberId, commentId);
            return comment(commentId, accountId);
        });
    }

    private Map<String, Object> addComment(String targetType, Long targetId, String content, Long accountId, Long parentId, Long toMemberId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String normalizedTargetType = normalizeTargetType(targetType);
        String normalizedContent = normalizeContent(content);
        if (jdbc == null) {
            return values.map("id", 1L, "targetType", normalizedTargetType, "targetId", targetId, "author", "demo_user", "content", normalizedContent, "date", values.today(), "mine", true, "likeCount", 0, "liked", false);
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Long receiverId = receiverForComment(jdbc, normalizedTargetType, targetId, parentId, toMemberId);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO comment_info(target_type, target_id, member_id, parent_id, root_id, to_member_id, content, status)
                        VALUES (?, ?, ?, ?, NULL, ?, ?, 'ACTIVE')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, normalizedTargetType);
                statement.setLong(2, targetId);
                statement.setLong(3, memberId);
                if (parentId == null || parentId == 0) {
                    statement.setObject(4, null);
                } else {
                    statement.setLong(4, parentId);
                }
                if (receiverId == null || receiverId == 0) {
                    statement.setObject(5, null);
                } else {
                    statement.setLong(5, receiverId);
                }
                statement.setString(6, normalizedContent);
                return statement;
            }, keyHolder);
            Long commentId = values.key(keyHolder);
            Long rootId = parentId == null || parentId == 0 ? commentId : parentId;
            jdbc.update("UPDATE comment_info SET root_id = ? WHERE id = ?", rootId, commentId);
            incrementCommentCount(jdbc, normalizedTargetType, targetId);
            if (receiverId != null && receiverId != 0 && !Objects.equals(receiverId, memberId)) {
                notificationDispatcher.dispatchToMember(receiverId, "COMMENT", commentTitle(normalizedTargetType, parentId), commentContent(normalizedTargetType, parentId), normalizedTargetType, targetId);
            }
            return comment(commentId, accountId);
        });
    }

    private PageResult<Map<String, Object>> comments(String targetType, Long targetId, Long accountId, int page, int size) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            long total = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM comment_info
                    WHERE target_type = ? AND target_id = ? AND status = 'ACTIVE' AND parent_id IS NULL AND deleted_at IS NULL
                    """, Long.class, targetType, targetId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT ci.id, ci.target_type, ci.target_id, ci.content, ci.created_at, ci.member_id, ci.parent_id, mp.nickname,
                           (SELECT COUNT(*) FROM user_interaction ui
                            WHERE ui.target_type = 'COMMENT' AND ui.target_id = ci.id
                              AND ui.action_type = 'LIKE' AND ui.status = 'ACTIVE' AND ui.deleted_at IS NULL) AS like_count
                    FROM comment_info ci
                    JOIN member_profile mp ON mp.id = ci.member_id
                    WHERE ci.target_type = ? AND ci.target_id = ? AND ci.status = 'ACTIVE' AND ci.parent_id IS NULL AND ci.deleted_at IS NULL
                    ORDER BY ci.created_at DESC
                    LIMIT ?, ?
                    """, mappings.commentMapper(accountId), targetType, targetId, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    private Map<String, Object> comment(Long commentId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", commentId, "content", "", "date", values.today(), "mine", true, "likeCount", 0, "liked", false);
        }
        try {
            return jdbc.queryForObject("""
                    SELECT ci.id, ci.target_type, ci.target_id, ci.content, ci.created_at, ci.member_id, ci.parent_id, mp.nickname,
                           (SELECT COUNT(*) FROM user_interaction ui
                            WHERE ui.target_type = 'COMMENT' AND ui.target_id = ci.id
                              AND ui.action_type = 'LIKE' AND ui.status = 'ACTIVE' AND ui.deleted_at IS NULL) AS like_count
                    FROM comment_info ci
                    JOIN member_profile mp ON mp.id = ci.member_id
                    WHERE ci.id = ? AND ci.deleted_at IS NULL
                    """, mappings.commentMapper(accountId), commentId);
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "comment does not exist");
        }
    }

    private Long receiverForComment(JdbcTemplate jdbc, String targetType, Long targetId, Long parentId, Long requestedToMemberId) {
        if (parentId != null && parentId != 0) {
            try {
                Long parentMemberId = jdbc.queryForObject("""
                        SELECT member_id
                        FROM comment_info
                        WHERE id = ? AND target_type = ? AND target_id = ?
                          AND parent_id IS NULL AND status = 'ACTIVE' AND deleted_at IS NULL
                        """, Long.class, parentId, targetType, targetId);
                if (requestedToMemberId != null && requestedToMemberId != 0 && !Objects.equals(requestedToMemberId, parentMemberId)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "reply target member does not match parent comment");
                }
                return parentMemberId;
            } catch (DataAccessException ignored) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "parent comment does not exist");
            }
        }
        try {
            if ("REQUEST_POST".equals(targetType)) {
                return jdbc.queryForObject("""
                        SELECT requester_id
                        FROM request_post
                        WHERE id = ? AND status = 'ONGOING' AND deleted_at IS NULL
                        """, Long.class, targetId);
            }
            return jdbc.queryForObject("""
                    SELECT publisher_id
                    FROM resource_info
                    WHERE id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL
                    """, Long.class, targetId);
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "comment target does not exist");
        }
    }

    private void incrementCommentCount(JdbcTemplate jdbc, String targetType, Long targetId) {
        if ("REQUEST_POST".equals(targetType)) {
            jdbc.update("UPDATE request_post SET comment_count = comment_count + 1 WHERE id = ?", targetId);
            return;
        }
        jdbc.update("UPDATE resource_info SET comment_count = comment_count + 1 WHERE id = ?", targetId);
    }

    private void decrementCommentCount(JdbcTemplate jdbc, String targetType, Long targetId) {
        if ("REQUEST_POST".equals(targetType)) {
            jdbc.update("UPDATE request_post SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = ?", targetId);
            return;
        }
        jdbc.update("UPDATE resource_info SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = ?", targetId);
    }

    private void ensureActiveComment(JdbcTemplate jdbc, Long commentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM comment_info
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """, Integer.class, commentId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "comment does not exist");
        }
    }

    private String normalizeTargetType(String targetType) {
        String normalized = values.firstNonBlank(targetType, "RESOURCE").toUpperCase();
        return switch (normalized) {
            case "REQUEST", "DEMAND", "REQUEST_POST" -> "REQUEST_POST";
            default -> "RESOURCE";
        };
    }

    private String normalizeContent(String content) {
        String normalized = values.firstNonBlank(content, "");
        if (normalized.isBlank() || normalized.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "comment content length must be between 1 and 500");
        }
        contentModerationService.requireClean(normalized);
        return normalized;
    }

    private String commentTitle(String targetType, Long parentId) {
        if (parentId != null && parentId != 0) {
            return "New comment reply";
        }
        return "REQUEST_POST".equals(targetType) ? "Request received a new comment" : "Resource received a new comment";
    }

    private String commentContent(String targetType, Long parentId) {
        if (parentId != null && parentId != 0) {
            return "Your comment received a new reply.";
        }
        return "REQUEST_POST".equals(targetType) ? "Your request received a new comment." : "Your resource received a new comment.";
    }

    private Map<String, Object> defaultResource() {
        return values.map(
                "id", 1L,
                "title", "demo resource",
                "summary", "demo resource summary",
                "description", "demo resource summary",
                "detail", "demo resource detail",
                "categoryId", 11L,
                "category1", "1",
                "category2", "11",
                "resourceType", "DOCUMENT",
                "type", "document",
                "status", "PUBLISHED",
                "author", "demo_user",
                "downloads", 136,
                "downloadCount", 136,
                "favoriteCount", 0,
                "likeCount", 0,
                "commentCount", 0,
                "score", 4.8,
                "ratingCount", 1,
                "date", values.today(),
                "publishedAt", values.today(),
                "tags", List.of("demo"),
                "attachments", List.of(values.map("id", 1L, "name", "demo.zip", "fileName", "demo.zip", "size", "2.0 MB", "type", "ZIP", "downloads", 136)),
                "fileName", "demo.zip",
                "fileSize", "2.0 MB",
                "liked", false,
                "favorited", false,
                "userRating", 0
        );
    }
}

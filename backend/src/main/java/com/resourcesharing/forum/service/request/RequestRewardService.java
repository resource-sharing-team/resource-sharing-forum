package com.resourcesharing.forum.service.request;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.domain.statemachine.RequestStateMachine;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.support.ContentModerationService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import com.resourcesharing.forum.service.system.AdminLogService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RequestRewardService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;
    private final ForumLookupService lookup;
    private final PointManager pointManager;
    private final AdminLogService adminLogService;
    private final NotificationDispatcher notificationDispatcher;
    private final ContentModerationService contentModerationService;

    public RequestRewardService(
            TxSupport txSupport,
            ValueSupport values,
            MappingSupport mappings,
            ForumLookupService lookup,
            PointManager pointManager,
            AdminLogService adminLogService,
            NotificationDispatcher notificationDispatcher,
            ContentModerationService contentModerationService
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.lookup = lookup;
        this.pointManager = pointManager;
        this.adminLogService = adminLogService;
        this.notificationDispatcher = notificationDispatcher;
        this.contentModerationService = contentModerationService;
    }

    public PageResult<Map<String, Object>> listRequests(Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(1, List.of(defaultRequest()), page, size);
        }
        try {
            String keyword = values.blankToNull(params.get("keyword"));
            StringBuilder where = new StringBuilder("WHERE rp.deleted_at IS NULL");
            List<Object> args = new ArrayList<>();
            if (keyword != null) {
                where.append(" AND (rp.title LIKE ? OR rp.content LIKE ?)");
                String like = "%" + keyword + "%";
                args.add(like);
                args.add(like);
            }
            long total = jdbc.queryForObject("SELECT COUNT(*) FROM request_post rp " + where, Long.class, args.toArray());
            args.add((page - 1) * size);
            args.add(size);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT rp.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM request_post rp
                    JOIN member_profile mp ON mp.id = rp.requester_id
                    LEFT JOIN resource_category c2 ON c2.id = rp.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    %s
                    ORDER BY rp.create_time DESC, rp.id DESC
                    LIMIT ?, ?
                    """.formatted(where), mappings.requestMapper(), args.toArray());
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(1, List.of(defaultRequest()), page, size);
        }
    }

    public Map<String, Object> createRequest(Long accountId, Map<String, Object> request) {
        String title = normalizeRequestTitle(values.value(request, "title", ""));
        String content = normalizeRequestContent(values.firstNonBlank(
                values.value(request, "content", ""),
                values.value(request, "description", "")
        ));
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return defaultRequest();
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            int reward = Math.max(0, (int) values.number(values.firstPresent(request, "rewardPoints", "points"), 0L).longValue());
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO request_post(requester_id, category_id, title, content, expected_format, reward_points, status, deadline_time)
                        VALUES (?, ?, ?, ?, ?, ?, 'ONGOING', ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, memberId);
                Long categoryId = values.number(values.firstPresent(request, "categoryId", "category2"), 11L);
                if (categoryId == 0) {
                    statement.setObject(2, null);
                } else {
                    statement.setLong(2, categoryId);
                }
                statement.setString(3, title);
                statement.setString(4, content);
                statement.setString(5, values.firstNonBlank(values.value(request, "expectedFormat", ""), values.value(request, "format", ""), "unlimited"));
                statement.setInt(6, reward);
                statement.setObject(7, null);
                return statement;
            }, keyHolder);
            Long requestId = values.key(keyHolder);
            if (reward > 0) {
                pointManager.freezeForRequest(memberId, reward, requestId);
            }
            insertRequestTags(jdbc, requestId, values.value(request, "tags", ""));
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, NULL, 'ONGOING', ?, 'Request published')
                    """, requestId, accountId);
            return requestPost(requestId);
        });
    }

    public Map<String, Object> requestDetail(Long requestId, Long accountId) {
        return values.map(
                "request", requestPost(requestId),
                "replies", listReplies(requestId, Map.of("page", "1", "size", "20")).list(),
                "comments", comments("REQUEST_POST", requestId, accountId, 1, 20).list()
        );
    }

    public void cancelRequest(Long requestId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Map<String, Object> row = jdbc.queryForObject("""
                    SELECT requester_id, reward_points, status
                    FROM request_post
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "requesterId", rs.getLong("requester_id"),
                    "reward", rs.getInt("reward_points"),
                    "status", rs.getString("status")
            ), requestId);
            if (!Objects.equals(values.number(row.get("requesterId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Only the publisher can cancel this request");
            }
            String reason = values.firstNonBlank(values.value(request, "reason", ""), "Publisher cancelled request");
            RequestStateMachine.assertCanTransit(String.valueOf(row.get("status")), "CANCELLED", "CANCEL", "MEMBER", true, reason);
            int reward = (int) values.number(row.get("reward"), 0L).longValue();
            if (reward > 0) {
                pointManager.refundRequest(requestId);
            }
            jdbc.update("UPDATE request_post SET status = 'CANCELLED', closed_time = NOW(3) WHERE id = ?", requestId);
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'CANCELLED', ?, ?)
                    """, requestId, row.get("status"), accountId, reason);
            return null;
        });
    }

    public PageResult<Map<String, Object>> listReplies(Long requestId, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            long total = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM request_reply
                    WHERE request_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                    """, Long.class, requestId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT rr.*, mp.nickname AS author_name
                    FROM request_reply rr
                    JOIN member_profile mp ON mp.id = rr.replier_id
                    WHERE rr.request_id = ? AND rr.status = 'ACTIVE' AND rr.deleted_at IS NULL
                    ORDER BY rr.is_accepted DESC, rr.create_time DESC
                    LIMIT ?, ?
                    """, mappings.replyMapper(), requestId, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    public Map<String, Object> replyRequest(Long requestId, Long accountId, Map<String, Object> request) {
        Long resourceId = values.number(values.firstPresent(request, "resourceId", "referencedResourceId"), 0L);
        String externalUrl = values.blankToNull(values.value(request, "externalUrl", ""));
        String content = normalizeReplyContent(values.value(request, "content", ""), resourceId, externalUrl);
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", 1L, "requestId", requestId, "content", content, "accepted", false);
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Map<String, Object> post = jdbc.queryForObject("""
                    SELECT requester_id, status
                    FROM request_post
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map("requesterId", rs.getLong("requester_id"), "status", rs.getString("status")), requestId);
            if ("ONGOING".equals(post.get("status")) && Objects.equals(values.number(post.get("requesterId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot reply to your own request");
            }
            if (!"ONGOING".equals(post.get("status"))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Request is not ongoing");
            }
            ensureReferencedResourcePublished(jdbc, resourceId);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO request_reply(request_id, replier_id, content, resource_id, external_url, status)
                        VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, requestId);
                statement.setLong(2, memberId);
                statement.setString(3, content);
                if (resourceId == 0) {
                    statement.setObject(4, null);
                } else {
                    statement.setLong(4, resourceId);
                }
                statement.setString(5, externalUrl);
                return statement;
            }, keyHolder);
            jdbc.update("UPDATE request_post SET answer_count = answer_count + 1 WHERE id = ?", requestId);
            notificationDispatcher.dispatchToMember(values.number(post.get("requesterId"), 0L), "REQUEST_REPLY",
                    "Request received a new reply", "Your request received a new reply", "REQUEST_POST", requestId);
            return reply(values.key(keyHolder));
        });
    }

    private void ensureReferencedResourcePublished(JdbcTemplate jdbc, Long resourceId) {
        if (resourceId == null || resourceId == 0) {
            return;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM resource_info
                WHERE id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL
                """, Integer.class, resourceId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "referenced resource must be published");
        }
    }

    private String normalizeRequestTitle(String title) {
        String normalized = values.firstNonBlank(title);
        if (normalized.length() < 5 || normalized.length() > 80) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "request title length must be between 5 and 80");
        }
        contentModerationService.requireClean(normalized);
        return normalized;
    }

    private String normalizeRequestContent(String content) {
        String normalized = values.firstNonBlank(content);
        if (normalized.length() < 20 || normalized.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "request content length must be between 20 and 500");
        }
        contentModerationService.requireClean(normalized);
        return normalized;
    }

    private String normalizeReplyContent(String content, Long resourceId, String externalUrl) {
        String normalized = values.firstNonBlank(content);
        boolean hasResource = resourceId != null && resourceId != 0;
        boolean hasExternalUrl = externalUrl != null && !externalUrl.isBlank();
        if (normalized.isBlank() && !hasResource && !hasExternalUrl) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "reply content, referenced resource, or external URL is required");
        }
        if (!normalized.isBlank() && normalized.length() > 1000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "reply content length must be at most 1000");
        }
        if (!normalized.isBlank()) {
            contentModerationService.requireClean(normalized);
        }
        return normalized;
    }

    public Map<String, Object> settleRequest(Long requestId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", requestId, "status", "RESOLVED");
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Long replyId = values.number(values.firstPresent(request, "replyId", "answerId"), 0L);
            Map<String, Object> post = jdbc.queryForObject("""
                    SELECT requester_id, reward_points, status, accepted_reply_id
                    FROM request_post
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "requesterId", rs.getLong("requester_id"),
                    "reward", rs.getInt("reward_points"),
                    "status", rs.getString("status"),
                    "acceptedReplyId", rs.getObject("accepted_reply_id")
            ), requestId);
            if (!Objects.equals(values.number(post.get("requesterId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Only the publisher can accept a reply");
            }
            RequestStateMachine.assertCanTransit(String.valueOf(post.get("status")), "RESOLVED", "ACCEPT_REPLY", "MEMBER", true, "Accept reply and settle reward");
            if (post.get("acceptedReplyId") != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Request has already been settled");
            }
            Map<String, Object> reply = jdbc.queryForObject("""
                    SELECT replier_id
                    FROM request_reply
                    WHERE id = ? AND request_id = ? AND status = 'ACTIVE'
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map("replierId", rs.getLong("replier_id")), replyId, requestId);
            int reward = (int) values.number(post.get("reward"), 0L).longValue();
            if (reward > 0) {
                pointManager.transferReward(requestId, values.number(reply.get("replierId"), 0L));
            }
            jdbc.update("UPDATE request_reply SET is_accepted = 1, accepted_time = NOW(3) WHERE id = ?", replyId);
            jdbc.update("UPDATE request_post SET status = 'RESOLVED', accepted_reply_id = ?, resolved_time = NOW(3) WHERE id = ?", replyId, requestId);
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'RESOLVED', ?, 'Accept reply and settle reward')
                    """, requestId, post.get("status"), accountId);
            notificationDispatcher.dispatchToMember(values.number(reply.get("replierId"), 0L), "REQUEST_ACCEPTED",
                    "Your reply was accepted", "Your request reply was accepted and the reward has been settled", "REQUEST_POST", requestId);
            return requestPost(requestId);
        });
    }

    public Map<String, Object> closeRequestByAdmin(Long adminAccountId, Long requestId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", requestId, "status", "CLOSED");
        }
        return txSupport.required(() -> {
            Map<String, Object> post = jdbc.queryForObject("""
                    SELECT requester_id, reward_points, status
                    FROM request_post
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map("requesterId", rs.getLong("requester_id"), "reward", rs.getInt("reward_points"), "status", rs.getString("status")), requestId);
            String before = String.valueOf(post.get("status"));
            String reason = values.firstNonBlank(values.value(request, "reason", ""), "Admin closed request");
            RequestStateMachine.assertCanTransit(before, "CLOSED", "CLOSE", "ADMIN", false, reason);
            if ("ONGOING".equals(before) && values.number(post.get("reward"), 0L) > 0) {
                pointManager.refundRequest(requestId);
            }
            jdbc.update("UPDATE request_post SET status = 'CLOSED', closed_time = NOW(3) WHERE id = ?", requestId);
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'CLOSED', ?, ?)
                    """, requestId, before, adminAccountId, reason);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "REQUEST_CLOSE", "REQUEST_POST", requestId, before, "CLOSED");
            return requestPost(requestId);
        });
    }

    public Map<String, Object> deleteReplyByAdmin(Long adminAccountId, Long replyId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", replyId, "status", "DELETED");
        }
        return txSupport.required(() -> {
            Map<String, Object> reply = jdbc.queryForObject("""
                    SELECT request_id, status
                    FROM request_reply
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map("requestId", rs.getLong("request_id"), "status", rs.getString("status")), replyId);
            jdbc.update("UPDATE request_reply SET status = 'DELETED', deleted_at = NOW(3) WHERE id = ?", replyId);
            jdbc.update("UPDATE request_post SET answer_count = GREATEST(answer_count - 1, 0) WHERE id = ?", values.number(reply.get("requestId"), 0L));
            adminLogService.record(lookup.adminProfileId(adminAccountId), "REPLY_DELETE", "REQUEST_REPLY", replyId, String.valueOf(reply.get("status")), "DELETED");
            return values.map("id", replyId, "status", "DELETED");
        });
    }

    private Map<String, Object> requestPost(Long requestId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return defaultRequest();
        }
        try {
            return jdbc.queryForObject("""
                    SELECT rp.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM request_post rp
                    JOIN member_profile mp ON mp.id = rp.requester_id
                    LEFT JOIN resource_category c2 ON c2.id = rp.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    WHERE rp.id = ? AND rp.deleted_at IS NULL
                    """, mappings.requestMapper(), requestId);
        } catch (DataAccessException ignored) {
            return defaultRequest();
        }
    }

    private Map<String, Object> reply(Long replyId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", replyId, "accepted", false);
        }
        return jdbc.queryForObject("""
                SELECT rr.*, mp.nickname AS author_name
                FROM request_reply rr
                JOIN member_profile mp ON mp.id = rr.replier_id
                WHERE rr.id = ?
                """, mappings.replyMapper(), replyId);
    }

    private PageResult<Map<String, Object>> comments(String targetType, Long targetId, Long accountId, int page, int size) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            long total = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM comment_info
                    WHERE target_type = ? AND target_id = ? AND status = 'ACTIVE' AND parent_id IS NULL AND deleted_at IS NULL
                    """, Long.class, targetType, targetId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT ci.id, ci.target_type, ci.target_id, ci.content, ci.create_time, ci.member_id, ci.parent_id, mp.nickname
                    FROM comment_info ci
                    JOIN member_profile mp ON mp.id = ci.member_id
                    WHERE ci.target_type = ? AND ci.target_id = ? AND ci.status = 'ACTIVE' AND ci.parent_id IS NULL AND ci.deleted_at IS NULL
                    ORDER BY ci.create_time DESC
                    LIMIT ?, ?
                    """, mappings.commentMapper(accountId), targetType, targetId, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    private void insertRequestTags(JdbcTemplate jdbc, Long requestId, String tagsText) {
        for (String tag : values.splitTags(tagsText)) {
            Long tagId = ensureTag(jdbc, tag);
            jdbc.update("INSERT IGNORE INTO request_tag_rel(request_id, tag_id) VALUES (?, ?)", requestId, tagId);
        }
    }

    private Long ensureTag(JdbcTemplate jdbc, String tagName) {
        try {
            return jdbc.queryForObject("SELECT id FROM tag_info WHERE tag_name = ?", Long.class, tagName);
        } catch (DataAccessException ignored) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO tag_info(tag_name, use_count) VALUES (?, 1)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, tagName);
                return statement;
            }, keyHolder);
            return values.key(keyHolder);
        }
    }

    private Map<String, Object> defaultRequest() {
        return values.map(
                "id", 1L,
                "title", "Need a Spring Boot project template",
                "content", "Need a complete Spring Boot backend project template with auth, resource management, and tests.",
                "description", "Need a complete Spring Boot backend project template with auth, resource management, and tests.",
                "categoryId", 32L,
                "category1", "3",
                "category2", "32",
                "rewardPoints", 50,
                "points", 50,
                "replyCount", 0,
                "commentCount", 0,
                "author", "demo_user",
                "date", values.today(),
                "status", "ONGOING",
                "tags", List.of("Java", "SpringBoot"),
                "expectedFormat", "zip or Git repository link",
                "format", "zip or Git repository link"
        );
    }
}

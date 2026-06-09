package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ProfileSummaryService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;
    private final ForumLookupService lookup;
    private final MemberService memberService;

    public ProfileSummaryService(
            TxSupport txSupport,
            ValueSupport values,
            MappingSupport mappings,
            ForumLookupService lookup,
            MemberService memberService
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.lookup = lookup;
        this.memberService = memberService;
    }

    public Map<String, Object> summary(Long accountId) {
        Map<String, Object> profile = memberService.userProfile(accountId);
        JdbcTemplate jdbc = txSupport.jdbc();
        Long memberId = lookup.memberId(accountId);
        if (jdbc == null || memberId == null) {
            return values.map(
                    "profile", profile,
                    "resources", List.of(),
                    "demands", List.of(),
                    "favorites", List.of(),
                    "likes", List.of(),
                    "messages", List.of(),
                    "loginLogs", List.of()
            );
        }
        return values.map(
                "profile", profile,
                "resources", myResources(jdbc, accountId, memberId),
                "demands", myRequests(jdbc, memberId),
                "favorites", interactedResources(jdbc, accountId, memberId, "FAVORITE"),
                "likes", interactedResources(jdbc, accountId, memberId, "LIKE"),
                "messages", messages(jdbc, memberId),
                "loginLogs", loginLogs(jdbc, accountId)
        );
    }

    private List<Map<String, Object>> myResources(JdbcTemplate jdbc, Long accountId, Long memberId) {
        try {
            return jdbc.query("""
                    SELECT r.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM resource_info r
                    JOIN member_profile mp ON mp.id = r.publisher_id
                    LEFT JOIN resource_category c2 ON c2.id = r.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    WHERE r.publisher_id = ? AND r.deleted_at IS NULL
                    ORDER BY r.updated_at DESC, r.id DESC
                    LIMIT 10
                    """, mappings.resourceMapper(accountId), memberId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> myRequests(JdbcTemplate jdbc, Long memberId) {
        try {
            return jdbc.query("""
                    SELECT rp.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM request_post rp
                    JOIN member_profile mp ON mp.id = rp.requester_id
                    LEFT JOIN resource_category c2 ON c2.id = rp.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    WHERE rp.requester_id = ? AND rp.deleted_at IS NULL
                    ORDER BY rp.updated_at DESC, rp.id DESC
                    LIMIT 10
                    """, mappings.requestMapper(), memberId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> interactedResources(JdbcTemplate jdbc, Long accountId, Long memberId, String actionType) {
        try {
            return jdbc.query("""
                    SELECT r.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM user_interaction ui
                    JOIN resource_info r ON r.id = ui.target_id
                    JOIN member_profile mp ON mp.id = r.publisher_id
                    LEFT JOIN resource_category c2 ON c2.id = r.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    WHERE ui.member_id = ?
                      AND ui.target_type = 'RESOURCE'
                      AND ui.action_type = ?
                      AND ui.status = 'ACTIVE'
                      AND ui.deleted_at IS NULL
                      AND r.status = 'PUBLISHED'
                      AND r.deleted_at IS NULL
                    ORDER BY ui.updated_at DESC, ui.id DESC
                    LIMIT 10
                    """, mappings.resourceMapper(accountId), memberId, actionType);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> messages(JdbcTemplate jdbc, Long memberId) {
        try {
            return jdbc.query("""
                    SELECT id, title, content, is_read, created_at
                    FROM system_notice
                    WHERE receiver_id = ? AND deleted_at IS NULL
                    ORDER BY created_at DESC, id DESC
                    LIMIT 10
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "title", rs.getString("title"),
                    "content", rs.getString("content"),
                    "unread", rs.getInt("is_read") == 0,
                    "date", values.date(rs.getObject("created_at", LocalDateTime.class))
            ), memberId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> loginLogs(JdbcTemplate jdbc, Long accountId) {
        try {
            return jdbc.query("""
                    SELECT id, login_ip, user_agent, result, fail_reason, created_at
                    FROM login_record
                    WHERE account_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 10
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "ip", values.firstNonBlank(rs.getString("login_ip"), "127.0.0.1"),
                    "device", values.firstNonBlank(rs.getString("user_agent"), "浏览器"),
                    "location", loginLocation(rs.getString("result"), rs.getString("fail_reason")),
                    "time", String.valueOf(rs.getObject("created_at", LocalDateTime.class))
            ), accountId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private String loginLocation(String result, String failReason) {
        if ("SUCCESS".equals(result)) {
            return "登录成功";
        }
        return values.firstNonBlank(failReason, "登录失败");
    }
}

package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UserCenterService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;
    private final ForumLookupService lookup;

    public UserCenterService(TxSupport txSupport, ValueSupport values, MappingSupport mappings, ForumLookupService lookup) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.lookup = lookup;
    }

    public PageResult<Map<String, Object>> resources(Long accountId, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        Long memberId = requireMember(accountId);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            String status = values.blankToNull(params.get("status"));
            StringBuilder where = new StringBuilder("WHERE r.publisher_id = ? AND r.deleted_at IS NULL");
            List<Object> args = new ArrayList<>();
            args.add(memberId);
            if (status != null) {
                where.append(" AND r.status = ?");
                args.add(status);
            }
            long total = count(jdbc, "SELECT COUNT(*) FROM resource_info r " + where, args);
            args.add((page - 1) * size);
            args.add(size);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT r.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM resource_info r
                    JOIN member_profile mp ON mp.id = r.publisher_id
                    LEFT JOIN resource_category c2 ON c2.id = r.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    %s
                    ORDER BY r.updated_at DESC, r.id DESC
                    LIMIT ?, ?
                    """.formatted(where), mappings.resourceMapper(accountId), args.toArray());
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    public PageResult<Map<String, Object>> requests(Long accountId, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        Long memberId = requireMember(accountId);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            String status = values.blankToNull(params.get("status"));
            StringBuilder where = new StringBuilder("WHERE rp.requester_id = ? AND rp.deleted_at IS NULL");
            List<Object> args = new ArrayList<>();
            args.add(memberId);
            if (status != null) {
                where.append(" AND rp.status = ?");
                args.add(status);
            }
            long total = count(jdbc, "SELECT COUNT(*) FROM request_post rp " + where, args);
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
                    ORDER BY rp.updated_at DESC, rp.id DESC
                    LIMIT ?, ?
                    """.formatted(where), mappings.requestMapper(), args.toArray());
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    public PageResult<Map<String, Object>> replies(Long accountId, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        Long memberId = requireMember(accountId);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            String accepted = values.blankToNull(params.get("accepted"));
            StringBuilder where = new StringBuilder("WHERE rr.replier_id = ? AND rr.deleted_at IS NULL");
            List<Object> args = new ArrayList<>();
            args.add(memberId);
            if (accepted != null) {
                where.append(" AND rr.is_accepted = ?");
                args.add(Boolean.parseBoolean(accepted) ? 1 : 0);
            }
            long total = count(jdbc, "SELECT COUNT(*) FROM request_reply rr " + where, args);
            args.add((page - 1) * size);
            args.add(size);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT rr.id, rr.request_id, rr.content, rr.resource_id, rr.external_url,
                           rr.status, rr.is_accepted, rr.created_at, rp.title AS request_title
                    FROM request_reply rr
                    JOIN request_post rp ON rp.id = rr.request_id
                    %s
                    ORDER BY rr.created_at DESC, rr.id DESC
                    LIMIT ?, ?
                    """.formatted(where), (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "requestId", rs.getLong("request_id"),
                    "requestTitle", rs.getString("request_title"),
                    "content", rs.getString("content"),
                    "resourceId", rs.getObject("resource_id"),
                    "externalUrl", rs.getString("external_url"),
                    "status", rs.getString("status"),
                    "accepted", rs.getInt("is_accepted") == 1,
                    "date", values.date(rs.getObject("created_at", LocalDateTime.class))
            ), args.toArray());
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    public PageResult<Map<String, Object>> favorites(Long accountId, Map<String, String> params) {
        return interactedResources(accountId, params, "FAVORITE");
    }

    public PageResult<Map<String, Object>> likes(Long accountId, Map<String, String> params) {
        return interactedResources(accountId, params, "LIKE");
    }

    public PageResult<Map<String, Object>> downloads(Long accountId, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        Long memberId = requireMember(accountId);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            long total = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM download_record
                    WHERE member_id = ? AND deleted_at IS NULL
                    """, Long.class, memberId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT id, resource_id, attachment_id, file_name, status, fail_reason, created_at
                    FROM download_record
                    WHERE member_id = ? AND deleted_at IS NULL
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?, ?
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "resourceId", rs.getLong("resource_id"),
                    "attachmentId", rs.getLong("attachment_id"),
                    "fileName", rs.getString("file_name"),
                    "status", rs.getString("status"),
                    "failReason", rs.getString("fail_reason"),
                    "downloadTime", String.valueOf(rs.getObject("created_at", LocalDateTime.class))
            ), memberId, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    public PageResult<Map<String, Object>> loginRecords(Long accountId, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "please log in before viewing login records");
        }
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            Long total = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM login_record
                    WHERE account_id = ?
                    """, Long.class, accountId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT id, login_ip, user_agent, result, fail_reason, created_at
                    FROM login_record
                    WHERE account_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ?, ?
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "ip", values.firstNonBlank(rs.getString("login_ip"), "127.0.0.1"),
                    "device", values.firstNonBlank(rs.getString("user_agent"), "browser"),
                    "result", rs.getString("result"),
                    "location", values.firstNonBlank(rs.getString("fail_reason"), rs.getString("result")),
                    "time", String.valueOf(rs.getObject("created_at", LocalDateTime.class))
            ), accountId, (page - 1) * size, size);
            return new PageResult<>(total == null ? 0 : total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    private PageResult<Map<String, Object>> interactedResources(Long accountId, Map<String, String> params, String actionType) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        Long memberId = requireMember(accountId);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            String targetType = values.firstNonBlank(params.get("targetType"), "RESOURCE");
            if (!"RESOURCE".equalsIgnoreCase(targetType)) {
                return new PageResult<>(0, List.of(), page, size);
            }
            long total = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM user_interaction ui
                    JOIN resource_info r ON r.id = ui.target_id
                    WHERE ui.member_id = ? AND ui.target_type = 'RESOURCE' AND ui.action_type = ?
                      AND ui.status = 'ACTIVE' AND ui.deleted_at IS NULL
                      AND r.deleted_at IS NULL
                    """, Long.class, memberId, actionType);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT r.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM user_interaction ui
                    JOIN resource_info r ON r.id = ui.target_id
                    JOIN member_profile mp ON mp.id = r.publisher_id
                    LEFT JOIN resource_category c2 ON c2.id = r.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    WHERE ui.member_id = ? AND ui.target_type = 'RESOURCE' AND ui.action_type = ?
                      AND ui.status = 'ACTIVE' AND ui.deleted_at IS NULL
                      AND r.deleted_at IS NULL
                    ORDER BY ui.updated_at DESC, ui.id DESC
                    LIMIT ?, ?
                    """, mappings.resourceMapper(accountId), memberId, actionType, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    private Long requireMember(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "please log in before viewing user data");
        }
        Long memberId = lookup.memberId(accountId);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "member profile does not exist or is disabled");
        }
        return memberId;
    }

    private static long count(JdbcTemplate jdbc, String sql, List<Object> args) {
        Long total = jdbc.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0 : total;
    }
}

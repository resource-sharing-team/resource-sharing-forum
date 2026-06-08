package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.domain.ResourceType;
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
public class PublicMetadataService {
    private final TxSupport txSupport;
    private final ValueSupport values;

    public PublicMetadataService(TxSupport txSupport, ValueSupport values) {
        this.txSupport = txSupport;
        this.values = values;
    }

    public List<Map<String, Object>> categories(String status) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return List.of(defaultCategory());
        }
        String requestedStatus = values.firstNonBlank(status, "ENABLED");
        try {
            List<Map<String, Object>> roots = jdbc.query("""
                    SELECT id, category_name, level_no, sort_order
                    FROM resource_category
                    WHERE parent_id IS NULL AND status = ? AND deleted_at IS NULL
                    ORDER BY sort_order ASC, id ASC
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "name", rs.getString("category_name"),
                    "level", rs.getInt("level_no"),
                    "sortOrder", rs.getInt("sort_order"),
                    "children", new ArrayList<Map<String, Object>>()
            ), requestedStatus);
            for (Map<String, Object> root : roots) {
                Long parentId = values.number(root.get("id"), 0L);
                List<Map<String, Object>> children = jdbc.query("""
                        SELECT id, category_name, level_no, sort_order
                        FROM resource_category
                        WHERE parent_id = ? AND status = ? AND deleted_at IS NULL
                        ORDER BY sort_order ASC, id ASC
                        """, (rs, rowNum) -> values.map(
                        "id", rs.getLong("id"),
                        "name", rs.getString("category_name"),
                        "level", rs.getInt("level_no"),
                        "sortOrder", rs.getInt("sort_order")
                ), parentId, requestedStatus);
                root.put("children", children);
            }
            return roots;
        } catch (DataAccessException ignored) {
            return List.of(defaultCategory());
        }
    }

    public List<Map<String, Object>> suggestTags(String keyword, Integer limit) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int safeLimit = Math.max(1, Math.min(50, limit == null ? 10 : limit));
        if (jdbc == null) {
            return List.of(values.map("id", 1L, "name", "Java", "useCount", 10));
        }
        try {
            String normalized = values.blankToNull(keyword);
            if (normalized == null) {
                return jdbc.query("""
                        SELECT id, tag_name, use_count
                        FROM tag_info
                        WHERE status = 'ENABLED' AND deleted_at IS NULL
                        ORDER BY use_count DESC, id ASC
                        LIMIT ?
                        """, (rs, rowNum) -> tagRow(rs.getLong("id"), rs.getString("tag_name"), rs.getInt("use_count")), safeLimit);
            }
            String like = "%" + normalized + "%";
            return jdbc.query("""
                    SELECT id, tag_name, use_count
                    FROM tag_info
                    WHERE status = 'ENABLED' AND deleted_at IS NULL AND tag_name LIKE ?
                    ORDER BY use_count DESC, id ASC
                    LIMIT ?
                    """, (rs, rowNum) -> tagRow(rs.getLong("id"), rs.getString("tag_name"), rs.getInt("use_count")), like, safeLimit);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    public List<Map<String, Object>> resourceTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            types.add(values.map("value", type.name(), "label", label(type)));
        }
        return types;
    }

    public PageResult<Map<String, Object>> announcements(Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            Long total = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM platform_announcement
                    WHERE status = 'PUBLISHED' AND deleted_at IS NULL
                      AND (publish_time IS NULL OR publish_time <= NOW(3))
                      AND (offline_time IS NULL OR offline_time > NOW(3))
                    """, Long.class);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT id, title, content, publish_time, create_time
                    FROM platform_announcement
                    WHERE status = 'PUBLISHED' AND deleted_at IS NULL
                      AND (publish_time IS NULL OR publish_time <= NOW(3))
                      AND (offline_time IS NULL OR offline_time > NOW(3))
                    ORDER BY publish_time DESC, id DESC
                    LIMIT ?, ?
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "title", rs.getString("title"),
                    "content", rs.getString("content"),
                    "publishTime", time(rs.getObject("publish_time", LocalDateTime.class), rs.getObject("create_time", LocalDateTime.class))
            ), (page - 1) * size, size);
            return new PageResult<>(total == null ? 0 : total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    private Map<String, Object> defaultCategory() {
        return values.map(
                "id", 1L,
                "name", "Default",
                "level", 1,
                "sortOrder", 0,
                "children", List.of(values.map("id", 2L, "name", "General", "level", 2, "sortOrder", 0))
        );
    }

    private Map<String, Object> tagRow(Long id, String name, int useCount) {
        return values.map("id", id, "name", name, "useCount", useCount);
    }

    private static String label(ResourceType type) {
        return switch (type) {
            case DOCUMENT -> "Document";
            case SOFTWARE -> "Software";
            case SOURCE_CODE -> "Source Code";
            case MATERIAL -> "Material";
            case COURSE -> "Course";
            case TEMPLATE -> "Template";
            case LINK -> "Link";
        };
    }

    private static String time(LocalDateTime publishTime, LocalDateTime createTime) {
        LocalDateTime value = publishTime == null ? createTime : publishTime;
        return value == null ? "" : value.toString();
    }
}

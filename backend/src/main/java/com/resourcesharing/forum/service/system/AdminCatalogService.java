package com.resourcesharing.forum.service.system;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AdminCatalogService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final AdminLogService adminLogService;

    public AdminCatalogService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            AdminLogService adminLogService
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.adminLogService = adminLogService;
    }

    public PageResult<Map<String, Object>> listCategories(Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String status = values.blankToNull(params.get("status"));
        String where = "WHERE deleted_at IS NULL" + (status == null ? "" : " AND status = ?");
        Object[] countArgs = status == null ? new Object[]{} : new Object[]{status};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM resource_category " + where, Long.class, countArgs);
        List<Object> args = new ArrayList<>(Arrays.asList(countArgs));
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, parent_id, category_name, level_no, status, sort_order, create_time
                FROM resource_category
                %s
                ORDER BY level_no ASC, sort_order ASC, id ASC
                LIMIT ?, ?
                """.formatted(where), (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "parentId", rs.getObject("parent_id"),
                "name", rs.getString("category_name"),
                "categoryName", rs.getString("category_name"),
                "level", rs.getInt("level_no"),
                "status", rs.getString("status"),
                "sortOrder", rs.getInt("sort_order"),
                "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class))
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> createCategory(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", 1L, "status", "ENABLED", "name", values.value(request, "name", ""));
        }
        return txSupport.required(() -> {
            String name = values.firstNonBlank(values.value(request, "name", ""), values.value(request, "categoryName", ""));
            if (name.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Category name is required");
            }
            Long parentId = values.number(values.firstPresent(request, "parentId"), 0L);
            int level = parentId == 0 ? 1 : 2;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO resource_category(parent_id, category_name, level_no, status, sort_order)
                        VALUES (?, ?, ?, 'ENABLED', ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                if (parentId == 0) {
                    statement.setObject(1, null);
                } else {
                    statement.setLong(1, parentId);
                }
                statement.setString(2, name);
                statement.setInt(3, level);
                statement.setInt(4, values.number(values.firstPresent(request, "sortOrder"), 0L).intValue());
                return statement;
            }, keyHolder);
            Long id = values.key(keyHolder);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "CATEGORY_CREATE", "CATEGORY", id, null, "ENABLED");
            return category(id);
        });
    }

    public Map<String, Object> updateCategory(Long adminAccountId, Long categoryId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", categoryId, "request", request);
        }
        return txSupport.required(() -> {
            Map<String, Object> before = category(categoryId);
            jdbc.update("""
                    UPDATE resource_category
                    SET category_name = COALESCE(?, category_name),
                        sort_order = COALESCE(?, sort_order),
                        status = COALESCE(?, status)
                    WHERE id = ? AND deleted_at IS NULL
                    """, values.nullable(request, "name"), values.firstPresent(request, "sortOrder"), values.nullable(request, "status"), categoryId);
            Map<String, Object> after = category(categoryId);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "CATEGORY_UPDATE", "CATEGORY", categoryId, String.valueOf(before.get("status")), String.valueOf(after.get("status")));
            return after;
        });
    }

    public Map<String, Object> disableCategory(Long adminAccountId, Long categoryId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", categoryId, "status", "DISABLED");
        }
        return txSupport.required(() -> {
            Map<String, Object> before = category(categoryId);
            jdbc.update("UPDATE resource_category SET status = 'DISABLED' WHERE id = ? AND deleted_at IS NULL", categoryId);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "CATEGORY_DISABLE", "CATEGORY", categoryId, String.valueOf(before.get("status")), "DISABLED");
            return category(categoryId);
        });
    }

    public PageResult<Map<String, Object>> listTags(Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String keyword = values.blankToNull(params.get("keyword"));
        String where = "WHERE deleted_at IS NULL" + (keyword == null ? "" : " AND tag_name LIKE ?");
        Object[] countArgs = keyword == null ? new Object[]{} : new Object[]{"%" + keyword + "%"};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tag_info " + where, Long.class, countArgs);
        List<Object> args = new ArrayList<>(Arrays.asList(countArgs));
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, tag_name, use_count, status, create_time
                FROM tag_info
                %s
                ORDER BY use_count DESC, id ASC
                LIMIT ?, ?
                """.formatted(where), (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "name", rs.getString("tag_name"),
                "tagName", rs.getString("tag_name"),
                "useCount", rs.getInt("use_count"),
                "status", rs.getString("status"),
                "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class))
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> createTag(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", 1L, "status", "ENABLED", "name", values.value(request, "name", ""));
        }
        return txSupport.required(() -> {
            String name = values.firstNonBlank(values.value(request, "name", ""), values.value(request, "tagName", ""));
            if (name.isBlank() || name.length() > 12) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Tag name is required and must be at most 12 characters");
            }
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO tag_info(tag_name, use_count, status)
                        VALUES (?, 0, 'ENABLED')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, name);
                return statement;
            }, keyHolder);
            Long id = values.key(keyHolder);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "TAG_CREATE", "TAG", id, null, "ENABLED");
            return tag(id);
        });
    }

    public Map<String, Object> disableTag(Long adminAccountId, Long tagId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", tagId, "status", "DISABLED");
        }
        return txSupport.required(() -> {
            Map<String, Object> before = tag(tagId);
            jdbc.update("UPDATE tag_info SET status = 'DISABLED' WHERE id = ? AND deleted_at IS NULL", tagId);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "TAG_DISABLE", "TAG", tagId, String.valueOf(before.get("status")), "DISABLED");
            return tag(tagId);
        });
    }

    public Map<String, Object> mergeTags(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("ok", true, "request", request);
        }
        return txSupport.required(() -> {
            Long sourceId = values.number(values.firstPresent(request, "sourceTagId", "sourceId"), 0L);
            Long targetId = values.number(values.firstPresent(request, "targetTagId", "targetId"), 0L);
            if (sourceId == 0 || targetId == 0 || Objects.equals(sourceId, targetId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid tag merge parameters");
            }
            Map<String, Object> source = tag(sourceId);
            Map<String, Object> target = tag(targetId);
            jdbc.update("""
                    INSERT IGNORE INTO resource_tag_rel(resource_id, tag_id)
                    SELECT resource_id, ? FROM resource_tag_rel WHERE tag_id = ?
                    """, targetId, sourceId);
            jdbc.update("DELETE FROM resource_tag_rel WHERE tag_id = ?", sourceId);
            jdbc.update("""
                    INSERT IGNORE INTO request_tag_rel(request_id, tag_id)
                    SELECT request_id, ? FROM request_tag_rel WHERE tag_id = ?
                    """, targetId, sourceId);
            jdbc.update("DELETE FROM request_tag_rel WHERE tag_id = ?", sourceId);
            jdbc.update("UPDATE tag_info SET status = 'DISABLED', use_count = 0 WHERE id = ?", sourceId);
            jdbc.update("""
                    UPDATE tag_info
                    SET use_count = (
                        SELECT COUNT(*) FROM (
                            SELECT resource_id AS owner_id FROM resource_tag_rel WHERE tag_id = ?
                            UNION ALL
                            SELECT request_id AS owner_id FROM request_tag_rel WHERE tag_id = ?
                        ) rels
                    )
                    WHERE id = ?
                    """, targetId, targetId, targetId);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "TAG_MERGE", "TAG", targetId, String.valueOf(source.get("name")), String.valueOf(target.get("name")));
            return values.map("ok", true, "source", tag(sourceId), "target", tag(targetId));
        });
    }

    private Map<String, Object> category(Long categoryId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", categoryId, "status", "ENABLED");
        }
        return jdbc.queryForObject("""
                SELECT id, parent_id, category_name, level_no, status, sort_order, create_time
                FROM resource_category
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "parentId", rs.getObject("parent_id"),
                "name", rs.getString("category_name"),
                "categoryName", rs.getString("category_name"),
                "level", rs.getInt("level_no"),
                "status", rs.getString("status"),
                "sortOrder", rs.getInt("sort_order"),
                "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class))
        ), categoryId);
    }

    private Map<String, Object> tag(Long tagId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", tagId, "status", "ENABLED");
        }
        return jdbc.queryForObject("""
                SELECT id, tag_name, use_count, status, create_time
                FROM tag_info
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "name", rs.getString("tag_name"),
                "tagName", rs.getString("tag_name"),
                "useCount", rs.getInt("use_count"),
                "status", rs.getString("status"),
                "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class))
        ), tagId);
    }
}

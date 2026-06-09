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

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminCatalogService {
    private static final List<Map<String, Object>> RESOURCE_TYPES = List.of(
            Map.of("code", (Object) "DOCUMENT", "name", "文档"),
            Map.of("code", (Object) "SOFTWARE", "name", "软件"),
            Map.of("code", (Object) "SOURCE_CODE", "name", "源码"),
            Map.of("code", (Object) "MATERIAL", "name", "素材"),
            Map.of("code", (Object) "COURSE", "name", "教程"),
            Map.of("code", (Object) "TEMPLATE", "name", "模板"),
            Map.of("code", (Object) "LINK", "name", "链接")
    );

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
        String keyword = values.blankToNull(params.get("keyword"));
        String level = values.blankToNull(params.get("level"));
        Long parentId = values.number(params.get("parentId"), null);
        StringBuilder where = new StringBuilder("WHERE c.deleted_at IS NULL");
        List<Object> countArgs = new ArrayList<>();
        if (status != null) {
            where.append(" AND c.status = ?");
            countArgs.add(status);
        }
        if (keyword != null) {
            where.append(" AND c.category_name LIKE ?");
            countArgs.add("%" + keyword + "%");
        }
        if ("1".equals(level) || "2".equals(level)) {
            where.append(" AND c.level_no = ?");
            countArgs.add(Integer.parseInt(level));
        }
        if (parentId != null) {
            where.append(" AND c.parent_id = ?");
            countArgs.add(parentId);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM resource_category c " + where, Long.class, countArgs.toArray());
        List<Object> args = new ArrayList<>(countArgs);
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT c.id, c.parent_id, c.category_name, c.level_no, c.status, c.sort_order, c.created_at,
                       p.category_name AS parent_name
                FROM resource_category c
                LEFT JOIN resource_category p ON p.id = c.parent_id
                %s
                ORDER BY c.level_no ASC, c.sort_order ASC, c.id ASC
                LIMIT ?, ?
                """.formatted(where), (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "parentId", rs.getObject("parent_id"),
                "name", rs.getString("category_name"),
                "categoryName", rs.getString("category_name"),
                "level", rs.getInt("level_no"),
                "parent", rs.getString("parent_name") == null ? "-" : rs.getString("parent_name"),
                "status", rs.getString("status"),
                "sortOrder", rs.getInt("sort_order"),
                "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class))
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
                throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称不能为空");
            }
            if (name.length() > 60) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称不能超过60个字符");
            }
            int level = categoryLevel(request);
            Long parentId = values.number(values.firstPresent(request, "parentId"), 0L);
            if (level == 1) {
                parentId = 0L;
            } else {
                validateSecondLevelParent(jdbc, parentId);
            }
            assertCategoryNameAvailable(jdbc, parentId, name, null);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            Long finalParentId = parentId;
            insertCategory(jdbc, keyHolder, finalParentId, name, level, values.number(values.firstPresent(request, "sortOrder"), 0L).intValue());
            Long id = values.key(keyHolder);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "CATEGORY_CREATE", "CATEGORY", id, null,
                    level == 1 ? "一级分类:" + name : "二级分类:" + name);
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
            String nextName = values.firstNonBlank(values.value(request, "name", ""), values.value(request, "categoryName", ""));
            if (!nextName.isBlank() && nextName.length() > 60) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称不能超过60个字符");
            }
            Long beforeParent = values.number(before.get("parentId"), 0L);
            if (!nextName.isBlank()) {
                assertCategoryNameAvailable(jdbc, beforeParent, nextName, categoryId);
            }
            String nextStatus = normalizeEnabledStatus(values.nullable(request, "status"));
            if ("DISABLED".equals(nextStatus)) {
                assertCanDisableCategory(jdbc, categoryId, before);
            }
            jdbc.update("""
                    UPDATE resource_category
                    SET category_name = COALESCE(?, category_name),
                        sort_order = COALESCE(?, sort_order),
                        status = COALESCE(?, status)
                    WHERE id = ? AND deleted_at IS NULL
                    """, nextName.isBlank() ? null : nextName, values.firstPresent(request, "sortOrder"), nextStatus, categoryId);
            Map<String, Object> after = category(categoryId);
            String operation = "ENABLED".equals(nextStatus) ? "CATEGORY_ENABLE" : "CATEGORY_UPDATE";
            adminLogService.record(lookup.adminProfileId(adminAccountId), operation, "CATEGORY", categoryId,
                    String.valueOf(before.get("status")), String.valueOf(after.get("status")));
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
            assertCanDisableCategory(jdbc, categoryId, before);
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
        String status = values.blankToNull(params.get("status"));
        StringBuilder where = new StringBuilder("WHERE deleted_at IS NULL");
        List<Object> countArgs = new ArrayList<>();
        if (keyword != null) {
            where.append(" AND tag_name LIKE ?");
            countArgs.add("%" + keyword + "%");
        }
        if (status != null) {
            where.append(" AND status = ?");
            countArgs.add(status);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tag_info " + where, Long.class, countArgs.toArray());
        List<Object> args = new ArrayList<>(countArgs);
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, tag_name, use_count, status, created_at
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
                "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class))
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
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名称不能为空且不能超过12个字符");
            }
            if (!normativeTagNames(jdbc).contains(name)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签必须来自一级分类、二级分类或资源类型规范词");
            }
            Long existingId = findTagId(jdbc, name);
            if (existingId != null) {
                Map<String, Object> existing = tag(existingId);
                if (!"ENABLED".equals(existing.get("status"))) {
                    jdbc.update("UPDATE tag_info SET status = 'ENABLED' WHERE id = ?", existingId);
                    adminLogService.record(lookup.adminProfileId(adminAccountId), "TAG_ENABLE", "TAG", existingId, String.valueOf(existing.get("status")), "ENABLED");
                    return tag(existingId);
                }
                return existing;
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

    public Map<String, Object> updateTag(Long adminAccountId, Long tagId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", tagId, "request", request);
        }
        return txSupport.required(() -> {
            Map<String, Object> before = tag(tagId);
            String name = values.firstNonBlank(values.value(request, "name", ""), values.value(request, "tagName", ""));
            String nextName = name.isBlank() ? null : name;
            if (nextName != null && nextName.length() > 12) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名称不能超过12个字符");
            }
            if (nextName != null && !normativeTagNames(jdbc).contains(nextName)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签必须来自一级分类、二级分类或资源类型规范词");
            }
            String nextStatus = normalizeEnabledStatus(values.nullable(request, "status"));
            jdbc.update("""
                    UPDATE tag_info
                    SET tag_name = COALESCE(?, tag_name),
                        status = COALESCE(?, status)
                    WHERE id = ? AND deleted_at IS NULL
                    """, nextName, nextStatus, tagId);
            Map<String, Object> after = tag(tagId);
            String operation = "ENABLED".equals(nextStatus) ? "TAG_ENABLE" : "TAG_UPDATE";
            adminLogService.record(lookup.adminProfileId(adminAccountId), operation, "TAG", tagId,
                    String.valueOf(before.get("name")), String.valueOf(after.get("name")));
            return after;
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

    public Map<String, Object> catalogOptions() {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            List<Map<String, Object>> types = resourceTypes();
            List<Map<String, Object>> candidates = types.stream()
                    .map(item -> tagCandidate(String.valueOf(item.get("name")), "资源类型", null, null, false, null))
                    .toList();
            return values.map("firstLevelCategories", List.of(), "secondLevelCategories", List.of(), "resourceTypes", types,
                    "tagCandidates", candidates, "missingTags", candidates);
        }
        List<Map<String, Object>> firstLevels = jdbc.query("""
                SELECT id, category_name, sort_order, status
                FROM resource_category
                WHERE deleted_at IS NULL AND level_no = 1
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "name", rs.getString("category_name"),
                "sortOrder", rs.getInt("sort_order"),
                "status", rs.getString("status")
        ));
        List<Map<String, Object>> secondLevels = jdbc.query("""
                SELECT c.id, c.parent_id, c.category_name, c.sort_order, c.status, p.category_name AS parent_name
                FROM resource_category c
                JOIN resource_category p ON p.id = c.parent_id
                WHERE c.deleted_at IS NULL AND c.level_no = 2
                ORDER BY p.sort_order ASC, c.sort_order ASC, c.id ASC
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "parentId", rs.getLong("parent_id"),
                "parentName", rs.getString("parent_name"),
                "name", rs.getString("category_name"),
                "sortOrder", rs.getInt("sort_order"),
                "status", rs.getString("status")
        ));
        List<Map<String, Object>> candidates = tagCandidates(jdbc);
        return values.map(
                "firstLevelCategories", firstLevels,
                "secondLevelCategories", secondLevels,
                "resourceTypes", resourceTypes(),
                "tagCandidates", candidates,
                "missingTags", candidates.stream().filter(item -> !Boolean.TRUE.equals(item.get("exists"))).toList()
        );
    }

    public Map<String, Object> backfillNormativeTags(Long adminAccountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("createdCount", 0, "createdTags", List.of());
        }
        return txSupport.required(() -> {
            List<String> created = new ArrayList<>();
            for (String name : normativeTagNames(jdbc)) {
                if (name.length() <= 12 && findTagId(jdbc, name) == null) {
                    jdbc.update("INSERT INTO tag_info(tag_name, use_count, status) VALUES (?, 0, 'ENABLED')", name);
                    created.add(name);
                }
            }
            adminLogService.record(lookup.adminProfileId(adminAccountId), "TAG_BACKFILL", "TAG", null, null,
                    "补齐规范标签:" + created.size());
            return values.map("createdCount", created.size(), "createdTags", created, "options", catalogOptions());
        });
    }

    private Map<String, Object> category(Long categoryId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", categoryId, "status", "ENABLED");
        }
        return jdbc.queryForObject("""
                SELECT c.id, c.parent_id, c.category_name, c.level_no, c.status, c.sort_order, c.created_at,
                       p.category_name AS parent_name
                FROM resource_category c
                LEFT JOIN resource_category p ON p.id = c.parent_id
                WHERE c.id = ? AND c.deleted_at IS NULL
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "parentId", rs.getObject("parent_id"),
                "name", rs.getString("category_name"),
                "categoryName", rs.getString("category_name"),
                "level", rs.getInt("level_no"),
                "parent", rs.getString("parent_name") == null ? "-" : rs.getString("parent_name"),
                "status", rs.getString("status"),
                "sortOrder", rs.getInt("sort_order"),
                "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class))
        ), categoryId);
    }

    private Map<String, Object> tag(Long tagId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", tagId, "status", "ENABLED");
        }
        return jdbc.queryForObject("""
                SELECT id, tag_name, use_count, status, created_at
                FROM tag_info
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "name", rs.getString("tag_name"),
                "tagName", rs.getString("tag_name"),
                "useCount", rs.getInt("use_count"),
                "status", rs.getString("status"),
                "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class))
        ), tagId);
    }

    private void insertCategory(JdbcTemplate jdbc, KeyHolder keyHolder, Long parentId, String name, int level, int sortOrder) {
        try {
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
                statement.setInt(4, sortOrder);
                return statement;
            }, keyHolder);
        } catch (RuntimeException ex) {
            if (containsDuplicateKey(ex)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "同一父级下分类名称不能重复");
            }
            throw ex;
        }
    }

    private int categoryLevel(Map<String, Object> request) {
        Object levelValue = values.firstPresent(request, "level", "levelNo");
        Long parentId = values.number(values.firstPresent(request, "parentId"), 0L);
        if (levelValue == null) {
            return parentId == 0 ? 1 : 2;
        }
        String text = String.valueOf(levelValue).trim();
        if ("1".equals(text) || "一级分类".equals(text) || "FIRST".equalsIgnoreCase(text) || "PARENT".equalsIgnoreCase(text)) {
            return 1;
        }
        if ("2".equals(text) || "二级分类".equals(text) || "SECOND".equalsIgnoreCase(text) || "CHILD".equalsIgnoreCase(text)) {
            return 2;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "分类层级只允许一级或二级");
    }

    private void validateSecondLevelParent(JdbcTemplate jdbc, Long parentId) {
        if (parentId == null || parentId == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新增二级分类必须选择启用的一级分类");
        }
        List<Map<String, Object>> parents = jdbc.query("""
                SELECT id, level_no, status
                FROM resource_category
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> values.map("id", rs.getLong("id"), "level", rs.getInt("level_no"), "status", rs.getString("status")), parentId);
        if (parents.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "父级分类不存在");
        }
        Map<String, Object> parent = parents.get(0);
        if (values.number(parent.get("level"), 0L) != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二级分类的父级必须是一级分类");
        }
        if (!"ENABLED".equals(parent.get("status"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二级分类的父级必须处于启用状态");
        }
    }

    private void assertCategoryNameAvailable(JdbcTemplate jdbc, Long parentId, String name, Long excludedId) {
        String parentPredicate = parentId == null || parentId == 0 ? "parent_id IS NULL" : "parent_id = ?";
        List<Object> args = new ArrayList<>();
        if (parentId != null && parentId != 0) {
            args.add(parentId);
        }
        args.add(name);
        if (excludedId != null) {
            args.add(excludedId);
        }
        long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM resource_category
                WHERE deleted_at IS NULL AND %s AND category_name = ? %s
                """.formatted(parentPredicate, excludedId == null ? "" : "AND id <> ?"), Long.class, args.toArray());
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一父级下分类名称不能重复");
        }
    }

    private void assertCanDisableCategory(JdbcTemplate jdbc, Long categoryId, Map<String, Object> category) {
        if (values.number(category.get("level"), 0L) != 1) {
            return;
        }
        long enabledChildren = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM resource_category
                WHERE parent_id = ? AND status = 'ENABLED' AND deleted_at IS NULL
                """, Long.class, categoryId);
        if (enabledChildren > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该一级分类下仍有启用的二级分类，请先禁用子分类");
        }
    }

    private String normalizeEnabledStatus(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "启用", "ENABLED", "NORMAL" -> "ENABLED";
            case "禁用", "DISABLED" -> "DISABLED";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只允许启用或禁用");
        };
    }

    private Set<String> normativeTagNames(JdbcTemplate jdbc) {
        Set<String> names = new LinkedHashSet<>();
        jdbc.query("""
                SELECT category_name
                FROM resource_category
                WHERE deleted_at IS NULL AND status = 'ENABLED'
                ORDER BY level_no ASC, sort_order ASC, id ASC
                """, rs -> {
            String name = rs.getString("category_name");
            if (name != null && !name.isBlank() && name.length() <= 12) {
                names.add(name);
            }
        });
        resourceTypes().forEach(item -> names.add(String.valueOf(item.get("name"))));
        return names;
    }

    private List<Map<String, Object>> tagCandidates(JdbcTemplate jdbc) {
        Map<String, Map<String, Object>> existing = jdbc.query("""
                SELECT id, tag_name, status
                FROM tag_info
                WHERE deleted_at IS NULL
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "name", rs.getString("tag_name"),
                "status", rs.getString("status")
        )).stream().collect(java.util.stream.Collectors.toMap(
                item -> String.valueOf(item.get("name")),
                item -> item,
                (left, right) -> left,
                java.util.LinkedHashMap::new
        ));
        List<Map<String, Object>> candidates = new ArrayList<>();
        jdbc.query("""
                SELECT id, category_name, level_no, parent_id
                FROM resource_category
                WHERE deleted_at IS NULL AND status = 'ENABLED'
                ORDER BY level_no ASC, sort_order ASC, id ASC
                """, rs -> {
            String source = rs.getInt("level_no") == 1 ? "一级分类" : "二级分类";
            candidates.add(tagCandidate(rs.getString("category_name"), source, rs.getLong("id"), rs.getObject("parent_id"), existing.containsKey(rs.getString("category_name")), existing.get(rs.getString("category_name"))));
        });
        for (Map<String, Object> type : resourceTypes()) {
            String name = String.valueOf(type.get("name"));
            candidates.add(tagCandidate(name, "资源类型", type.get("code"), null, existing.containsKey(name), existing.get(name)));
        }
        return candidates;
    }

    private Map<String, Object> tagCandidate(String name, String source, Object sourceId, Object parentId, boolean exists, Map<String, Object> existing) {
        return values.map(
                "name", name,
                "source", source,
                "sourceId", sourceId,
                "parentId", parentId,
                "exists", exists,
                "tagId", existing == null ? null : existing.get("id"),
                "status", existing == null ? "MISSING" : existing.get("status")
        );
    }

    private List<Map<String, Object>> resourceTypes() {
        return RESOURCE_TYPES.stream().map(item -> values.map("code", item.get("code"), "name", item.get("name"))).toList();
    }

    private Long findTagId(JdbcTemplate jdbc, String name) {
        List<Long> ids = jdbc.query("SELECT id FROM tag_info WHERE tag_name = ? AND deleted_at IS NULL", (rs, rowNum) -> rs.getLong("id"), name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean containsDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23000".equals(sqlException.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("duplicate")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

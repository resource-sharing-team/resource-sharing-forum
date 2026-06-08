package com.resourcesharing.forum.service.system;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AdminDashboardService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final AdminLogService adminLogService;
    private final AdminCatalogService adminCatalogService;

    public AdminDashboardService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            AdminLogService adminLogService,
            AdminCatalogService adminCatalogService
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.adminLogService = adminLogService;
        this.adminCatalogService = adminCatalogService;
    }

    public Map<String, Object> content(Map<String, String> params) {
        String section = values.blankToNull(params.get("section"));
        if (section != null) {
            return sectionResult(section, contentPage(section, params));
        }
        JdbcTemplate jdbc = txSupport.jdbc();
        int size = adminSize(params, 10);
        if (jdbc == null) {
            return values.map("auditRows", List.of(), "resourceRows", List.of(), "requestRows", List.of(), "commentRows", List.of());
        }
        return values.map(
                "auditRows", resources(jdbc, List.of("PENDING_REVIEW", "REJECTED"), size),
                "resourceRows", resources(jdbc, List.of("PUBLISHED", "OFFLINE", "COPYRIGHT_DOWN", "REVIEWING_RISK"), size),
                "requestRows", requests(jdbc, size),
                "commentRows", comments(jdbc, size)
        );
    }

    public PageResult<Map<String, Object>> users(Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = adminSize(params, 10);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String keyword = values.blankToNull(params.get("keyword"));
        String status = accountStatus(values.blankToNull(params.get("status")));
        StringBuilder where = new StringBuilder("WHERE ua.role = 'USER' AND ua.deleted_at IS NULL AND mp.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (keyword != null) {
            where.append(" AND (ua.username LIKE ? OR ua.email LIKE ? OR mp.nickname LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            where.append(" AND ua.status = ?");
            args.add(status);
        }
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM member_profile mp
                JOIN user_account ua ON ua.id = mp.account_id
                %s
                """.formatted(where), Long.class, args.toArray());
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT mp.id, mp.nickname, ua.id AS account_id, ua.username, ua.email, ua.status, ua.create_time
                FROM member_profile mp
                JOIN user_account ua ON ua.id = mp.account_id
                %s
                ORDER BY ua.create_time DESC, mp.id DESC
                LIMIT ?, ?
                """.formatted(where), (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "accountId", rs.getLong("account_id"),
                "nickname", rs.getString("nickname"),
                "username", rs.getString("username"),
                "email", rs.getString("email"),
                "rawStatus", rs.getString("status"),
                "status", accountStatusLabel(rs.getString("status")),
                "registeredAt", values.date(rs.getObject("create_time", java.time.LocalDateTime.class))
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> compliance(Map<String, String> params) {
        String section = values.blankToNull(params.get("section"));
        if (section != null) {
            return sectionResult(section, compliancePage(section, params));
        }
        JdbcTemplate jdbc = txSupport.jdbc();
        int size = adminSize(params, 10);
        if (jdbc == null) {
            return values.map("reports", List.of(), "complaints", List.of(), "appeals", List.of());
        }
        return values.map(
                "reports", reports(jdbc, false, size),
                "complaints", reports(jdbc, true, size),
                "appeals", appeals(jdbc, size)
        );
    }

    public Map<String, Object> catalog(Map<String, String> params) {
        String section = values.blankToNull(params.get("section"));
        if (section != null) {
            return sectionResult(section, catalogPage(section, params));
        }
        JdbcTemplate jdbc = txSupport.jdbc();
        int size = adminSize(params, 10);
        if (jdbc == null) {
            return values.map("categories", List.of(), "tags", List.of(), "rows", List.of());
        }
        List<Map<String, Object>> categories = catalogCategories(jdbc, size);
        List<Map<String, Object>> tags = catalogTags(jdbc, size);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(categories);
        rows.addAll(tags);
        return values.map("categories", categories, "tags", tags, "rows", rows);
    }

    public Map<String, Object> catalogOptions() {
        return adminCatalogService.catalogOptions();
    }

    public Map<String, Object> fullConfig() {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("memberLevels", List.of(), "scoreRules", List.of(), "systemParams", List.of());
        }
        return values.map(
                "memberLevels", memberLevels(jdbc),
                "scoreRules", configItems(jdbc, true),
                "systemParams", configItems(jdbc, false)
        );
    }

    public Map<String, Object> updateMemberLevel(Long adminAccountId, Long levelId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", levelId, "request", request);
        }
        return txSupport.required(() -> {
            Long adminProfileId = lookup.adminProfileId(adminAccountId);
            Map<String, Object> before = memberLevel(jdbc, levelId);
            jdbc.update("""
                    UPDATE membership_level
                    SET level_name = COALESCE(?, level_name),
                        min_points = COALESCE(?, min_points),
                        max_points = ?,
                        daily_download_limit = COALESCE(?, daily_download_limit),
                        max_files_per_resource = COALESCE(?, max_files_per_resource),
                        reward_limit = COALESCE(?, reward_limit),
                        can_apply_top = COALESCE(?, can_apply_top)
                    WHERE id = ? AND deleted_at IS NULL
                    """,
                    values.nullable(request, "name"),
                    values.firstPresent(request, "min", "minPoints"),
                    nullableNumber(values.firstPresent(request, "max", "maxPoints")),
                    values.firstPresent(request, "downloads", "dailyDownloadLimit"),
                    values.firstPresent(request, "files", "maxFilesPerResource"),
                    values.firstPresent(request, "rewardLimit"),
                    booleanNumber(values.firstPresent(request, "canTop", "canApplyTop")),
                    levelId);
            Map<String, Object> after = memberLevel(jdbc, levelId);
            adminLogService.record(adminProfileId, "MEMBER_LEVEL_UPDATE", "MEMBERSHIP_LEVEL", levelId,
                    String.valueOf(before.get("name")), String.valueOf(after.get("name")));
            return after;
        });
    }

    public Map<String, Object> hideComment(Long adminAccountId, Long commentId) {
        return updateCommentStatus(adminAccountId, commentId, "HIDDEN");
    }

    public Map<String, Object> deleteComment(Long adminAccountId, Long commentId) {
        return updateCommentStatus(adminAccountId, commentId, "DELETED");
    }

    public Map<String, Object> restoreComment(Long adminAccountId, Long commentId) {
        return updateCommentStatus(adminAccountId, commentId, "ACTIVE");
    }

    private Map<String, Object> updateCommentStatus(Long adminAccountId, Long commentId, String targetStatus) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", commentId, "status", commentStatusLabel(targetStatus), "rawStatus", targetStatus);
        }
        return txSupport.required(() -> {
            Long adminProfileId = lookup.adminProfileId(adminAccountId);
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT id, target_type, target_id, status
                    FROM comment_info
                    WHERE id = ?
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "targetType", rs.getString("target_type"),
                    "targetId", rs.getLong("target_id"),
                    "status", rs.getString("status")
            ), commentId);
            jdbc.update("""
                    UPDATE comment_info
                    SET status = ?, deleted_at = IF(? = 'DELETED', NOW(3), NULL)
                    WHERE id = ?
                    """, targetStatus, targetStatus, commentId);
            adminLogService.record(adminProfileId, "COMMENT_" + targetStatus, "COMMENT", commentId,
                    String.valueOf(before.get("status")), targetStatus);
            return values.map("id", commentId, "rawStatus", targetStatus, "status", commentStatusLabel(targetStatus));
        });
    }

    private PageResult<Map<String, Object>> contentPage(String section, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = adminSize(params, 10);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        return switch (section) {
            case "audit" -> resourcesPage(jdbc, List.of("PENDING_REVIEW", "REJECTED"), page, size);
            case "resource", "status" -> resourcesPage(jdbc, List.of("PUBLISHED", "OFFLINE", "COPYRIGHT_DOWN", "REVIEWING_RISK"), page, size);
            case "request" -> requestsPage(jdbc, page, size);
            case "comment" -> commentsPage(jdbc, page, size);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "未知管理端内容分区");
        };
    }

    private PageResult<Map<String, Object>> compliancePage(String section, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = adminSize(params, 10);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        return switch (section) {
            case "report" -> reportsPage(jdbc, false, page, size);
            case "copyright" -> reportsPage(jdbc, true, page, size);
            case "appeal" -> appealsPage(jdbc, page, size);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "未知管理端合规分区");
        };
    }

    private PageResult<Map<String, Object>> catalogPage(String section, Map<String, String> params) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = adminSize(params, 10);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        return switch (section) {
            case "all" -> hasCategoryOnlyFilter(params) ? catalogCategoriesPage(jdbc, page, size, params) : catalogAllPage(jdbc, page, size, params);
            case "category" -> catalogCategoriesPage(jdbc, page, size, params);
            case "tag" -> catalogTagsPage(jdbc, page, size, params);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "未知管理端分类分区");
        };
    }

    private Map<String, Object> sectionResult(String section, PageResult<Map<String, Object>> page) {
        return values.map(
                "section", section,
                "total", page.total(),
                "list", page.list(),
                "page", page.page(),
                "size", page.size()
        );
    }

    private List<Map<String, Object>> resources(JdbcTemplate jdbc, List<String> statuses, int size) {
        return resourcesPage(jdbc, statuses, 1, size).list();
    }

    private PageResult<Map<String, Object>> resourcesPage(JdbcTemplate jdbc, List<String> statuses, int page, int size) {
        String placeholders = String.join(",", statuses.stream().map(item -> "?").toList());
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM resource_info r
                WHERE r.deleted_at IS NULL AND r.status IN (%s)
                """.formatted(placeholders), Long.class, statuses.toArray());
        List<Object> args = new ArrayList<>(statuses);
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT r.id, r.title, r.status, mp.nickname AS user_name
                FROM resource_info r
                JOIN member_profile mp ON mp.id = r.publisher_id
                WHERE r.deleted_at IS NULL AND r.status IN (%s)
                ORDER BY r.update_time DESC, r.id DESC
                LIMIT ?, ?
                """.formatted(placeholders), (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "title", rs.getString("title"),
                "user", rs.getString("user_name"),
                "rawStatus", rs.getString("status"),
                "status", resourceStatusLabel(rs.getString("status"))
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    private List<Map<String, Object>> requests(JdbcTemplate jdbc, int size) {
        return requestsPage(jdbc, 1, size).list();
    }

    private PageResult<Map<String, Object>> requestsPage(JdbcTemplate jdbc, int page, int size) {
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM request_post rp
                WHERE rp.deleted_at IS NULL
                """, Long.class);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT rp.id, rp.title, rp.status, mp.nickname AS user_name
                FROM request_post rp
                JOIN member_profile mp ON mp.id = rp.requester_id
                WHERE rp.deleted_at IS NULL
                ORDER BY rp.update_time DESC, rp.id DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "title", rs.getString("title"),
                "user", rs.getString("user_name"),
                "rawStatus", rs.getString("status"),
                "status", requestStatusLabel(rs.getString("status"))
        ), (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    private List<Map<String, Object>> comments(JdbcTemplate jdbc, int size) {
        return commentsPage(jdbc, 1, size).list();
    }

    private PageResult<Map<String, Object>> commentsPage(JdbcTemplate jdbc, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM comment_info WHERE deleted_at IS NULL", Long.class);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, target_type, target_id, content, status
                FROM comment_info
                WHERE deleted_at IS NULL
                ORDER BY update_time DESC, id DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "content", rs.getString("content"),
                "target", rs.getString("target_type") + ":" + rs.getLong("target_id"),
                "targetType", rs.getString("target_type"),
                "targetId", rs.getLong("target_id"),
                "rawStatus", rs.getString("status"),
                "status", commentStatusLabel(rs.getString("status"))
        ), (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    private List<Map<String, Object>> reports(JdbcTemplate jdbc, boolean copyright, int size) {
        return reportsPage(jdbc, copyright, 1, size).list();
    }

    private PageResult<Map<String, Object>> reportsPage(JdbcTemplate jdbc, boolean copyright, int page, int size) {
        String predicate = copyright ? "rc.report_type = 'COPYRIGHT'" : "rc.report_type <> 'COPYRIGHT'";
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM report_complaint rc
                WHERE rc.deleted_at IS NULL AND %s
                """.formatted(predicate), Long.class);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT rc.id, rc.report_type, rc.target_type, rc.target_id, rc.title, rc.reason, rc.status,
                       COALESCE(ri.title, rc.title, CONCAT(rc.target_type, ':', rc.target_id)) AS target_name,
                       mp.nickname AS reporter_name
                FROM report_complaint rc
                LEFT JOIN resource_info ri ON rc.target_type = 'RESOURCE' AND ri.id = rc.target_id
                LEFT JOIN member_profile mp ON mp.id = rc.reporter_id
                WHERE rc.deleted_at IS NULL AND %s
                ORDER BY rc.create_time DESC, rc.id DESC
                LIMIT ?, ?
                """.formatted(predicate), (rs, rowNum) -> {
            String targetType = rs.getString("target_type");
            Long targetId = rs.getLong("target_id");
            String status = rs.getString("status");
            if (copyright) {
                return values.map(
                        "id", String.valueOf(rs.getLong("id")),
                        "rawId", rs.getLong("id"),
                        "resourceId", String.valueOf(targetId),
                        "rawResourceId", targetId,
                        "resourceName", rs.getString("target_name"),
                        "complainant", rs.getString("reporter_name") == null ? "版权投诉方" : rs.getString("reporter_name"),
                        "rawStatus", status,
                        "status", reportStatusLabel(status)
                );
            }
            return values.map(
                    "id", String.valueOf(rs.getLong("id")),
                    "rawId", rs.getLong("id"),
                    "targetId", String.valueOf(targetId),
                    "rawTargetId", targetId,
                    "targetType", targetType,
                    "target", targetLabel(targetType),
                    "type", rs.getString("report_type"),
                    "reason", rs.getString("reason"),
                    "action", reportAction(rs.getString("report_type"), targetType),
                    "rawStatus", status,
                    "status", reportStatusLabel(status)
            );
        }, (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    private List<Map<String, Object>> appeals(JdbcTemplate jdbc, int size) {
        return appealsPage(jdbc, 1, size).list();
    }

    private PageResult<Map<String, Object>> appealsPage(JdbcTemplate jdbc, int page, int size) {
        long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM appeal_record ar
                WHERE ar.deleted_at IS NULL
                """, Long.class);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT ar.id, ar.target_type, ar.target_id, ar.reason, ar.status, mp.nickname
                FROM appeal_record ar
                JOIN member_profile mp ON mp.id = ar.appellant_id
                WHERE ar.deleted_at IS NULL
                ORDER BY ar.create_time DESC, ar.id DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "targetId", String.valueOf(rs.getLong("target_id")),
                "targetType", rs.getString("target_type"),
                "reason", rs.getString("reason"),
                "appellant", rs.getString("nickname"),
                "rawStatus", rs.getString("status"),
                "status", appealStatusLabel(rs.getString("status"))
        ), (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    private List<Map<String, Object>> catalogCategories(JdbcTemplate jdbc, int size) {
        return catalogCategoriesPage(jdbc, 1, size, Map.of()).list();
    }

    private PageResult<Map<String, Object>> catalogCategoriesPage(JdbcTemplate jdbc, int page, int size, Map<String, String> params) {
        CategoryFilter filter = categoryFilter(params);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM resource_category c " + filter.where(), Long.class, filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT c.id, c.category_name, c.level_no, c.status, p.category_name AS parent_name,
                       (SELECT COUNT(*) FROM resource_info r WHERE r.category_id = c.id AND r.deleted_at IS NULL) AS relation_count
                FROM resource_category c
                LEFT JOIN resource_category p ON p.id = c.parent_id
                %s
                ORDER BY c.level_no ASC, c.sort_order ASC, c.id ASC
                LIMIT ?, ?
                """.formatted(filter.where()), (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "name", rs.getString("category_name"),
                "type", rs.getInt("level_no") == 1 ? "一级分类" : "二级分类",
                "parent", rs.getString("parent_name") == null ? "-" : rs.getString("parent_name"),
                "relationCount", rs.getLong("relation_count"),
                "rawStatus", rs.getString("status"),
                "status", enabledStatusLabel(rs.getString("status")),
                "kind", "CATEGORY"
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    private List<Map<String, Object>> catalogTags(JdbcTemplate jdbc, int size) {
        return catalogTagsPage(jdbc, 1, size, Map.of()).list();
    }

    private PageResult<Map<String, Object>> catalogTagsPage(JdbcTemplate jdbc, int page, int size, Map<String, String> params) {
        TagFilter filter = tagFilter(params);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tag_info t " + filter.where(), Long.class, filter.args().toArray());
        List<Object> args = new ArrayList<>(filter.args());
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT t.id, t.tag_name, t.use_count, t.status
                FROM tag_info t
                %s
                ORDER BY use_count DESC, id ASC
                LIMIT ?, ?
                """.formatted(filter.where()), (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "name", rs.getString("tag_name"),
                "type", "标签",
                "parent", "-",
                "relationCount", rs.getLong("use_count"),
                "rawStatus", rs.getString("status"),
                "status", enabledStatusLabel(rs.getString("status")),
                "kind", "TAG"
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    private PageResult<Map<String, Object>> catalogAllPage(JdbcTemplate jdbc, int page, int size, Map<String, String> params) {
        CategoryFilter categoryFilter = categoryFilter(params);
        TagFilter tagFilter = tagFilter(params);
        long categoryTotal = jdbc.queryForObject("SELECT COUNT(*) FROM resource_category c " + categoryFilter.where(), Long.class, categoryFilter.args().toArray());
        long tagTotal = jdbc.queryForObject("SELECT COUNT(*) FROM tag_info t " + tagFilter.where(), Long.class, tagFilter.args().toArray());
        List<Object> args = new ArrayList<>();
        args.addAll(categoryFilter.args());
        args.addAll(tagFilter.args());
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, name, type_label, parent_name, relation_count, status, kind
                FROM (
                    SELECT c.id,
                           c.category_name AS name,
                           IF(c.level_no = 1, '一级分类', '二级分类') AS type_label,
                           COALESCE(p.category_name, '-') AS parent_name,
                           (SELECT COUNT(*) FROM resource_info r WHERE r.category_id = c.id AND r.deleted_at IS NULL) AS relation_count,
                           c.status,
                           'CATEGORY' AS kind,
                           0 AS kind_order,
                           c.level_no AS level_order,
                           c.sort_order AS sort_order,
                           c.id AS row_order
                    FROM resource_category c
                    LEFT JOIN resource_category p ON p.id = c.parent_id
                    %s
                    UNION ALL
                    SELECT t.id,
                           t.tag_name AS name,
                           '标签' AS type_label,
                           '-' AS parent_name,
                           t.use_count AS relation_count,
                           t.status,
                           'TAG' AS kind,
                           1 AS kind_order,
                           99 AS level_order,
                           0 AS sort_order,
                           t.id AS row_order
                    FROM tag_info t
                    %s
                ) combined_rows
                ORDER BY kind_order ASC, level_order ASC, sort_order ASC, row_order ASC
                LIMIT ?, ?
                """.formatted(categoryFilter.where(), tagFilter.where()), (rs, rowNum) -> values.map(
                "id", String.valueOf(rs.getLong("id")),
                "rawId", rs.getLong("id"),
                "name", rs.getString("name"),
                "type", rs.getString("type_label"),
                "parent", rs.getString("parent_name"),
                "relationCount", rs.getLong("relation_count"),
                "rawStatus", rs.getString("status"),
                "status", enabledStatusLabel(rs.getString("status")),
                "kind", rs.getString("kind")
        ), args.toArray());
        return new PageResult<>(categoryTotal + tagTotal, list, page, size);
    }

    private List<Map<String, Object>> memberLevels(JdbcTemplate jdbc) {
        return jdbc.query("""
                SELECT id, level_name, min_points, max_points, daily_download_limit, max_files_per_resource, reward_limit, can_apply_top, status
                FROM membership_level
                WHERE deleted_at IS NULL
                ORDER BY sort_order ASC, id ASC
                """, (rs, rowNum) -> memberLevelRow(rs.getLong("id"), rs.getString("level_name"),
                rs.getLong("min_points"), rs.getObject("max_points"), rs.getLong("daily_download_limit"),
                rs.getLong("max_files_per_resource"), rs.getLong("reward_limit"), rs.getInt("can_apply_top"), rs.getString("status")));
    }

    private Map<String, Object> memberLevel(JdbcTemplate jdbc, Long levelId) {
        return jdbc.queryForObject("""
                SELECT id, level_name, min_points, max_points, daily_download_limit, max_files_per_resource, reward_limit, can_apply_top, status
                FROM membership_level
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> memberLevelRow(rs.getLong("id"), rs.getString("level_name"),
                rs.getLong("min_points"), rs.getObject("max_points"), rs.getLong("daily_download_limit"),
                rs.getLong("max_files_per_resource"), rs.getLong("reward_limit"), rs.getInt("can_apply_top"), rs.getString("status")), levelId);
    }

    private Map<String, Object> memberLevelRow(Long id, String name, Long min, Object max, Long downloads, Long files, Long rewardLimit, int canTop, String status) {
        return values.map(
                "id", String.valueOf(id),
                "rawId", id,
                "name", name,
                "min", String.valueOf(min),
                "max", max == null ? "无上限" : String.valueOf(max),
                "downloads", String.valueOf(downloads),
                "files", String.valueOf(files),
                "rewardLimit", String.valueOf(rewardLimit),
                "canTop", canTop == 1 ? "是" : "否",
                "rawStatus", status
        );
    }

    private List<Map<String, Object>> configItems(JdbcTemplate jdbc, boolean scoreRules) {
        String predicate = scoreRules
                ? "(config_key LIKE 'score.%' OR config_key LIKE 'point.%')"
                : "(config_key NOT LIKE 'score.%' AND config_key NOT LIKE 'point.%')";
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT config_key, config_value, value_type, description
                FROM system_config
                WHERE is_enabled = 1 AND %s
                ORDER BY id ASC
                """.formatted(predicate), (rs, rowNum) -> values.map(
                "key", rs.getString("config_key"),
                "label", rs.getString("description") == null ? rs.getString("config_key") : rs.getString("description"),
                "value", rs.getString("config_value"),
                "valueType", rs.getString("value_type")
        ));
        if (!rows.isEmpty() || !scoreRules) {
            return rows;
        }
        return List.of(
                values.map("key", "point.upload_reward", "label", "上传资源奖励", "value", "10", "valueType", "INTEGER"),
                values.map("key", "point.request_reward_min", "label", "悬赏最低积分", "value", "10", "valueType", "INTEGER")
        );
    }

    private Object nullableNumber(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "无上限".equals(String.valueOf(value))) {
            return null;
        }
        return values.number(value, null);
    }

    private Object booleanNumber(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return ("是".equals(text) || "true".equalsIgnoreCase(text) || "1".equals(text)) ? 1 : 0;
    }

    private String accountStatus(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "正常" -> "NORMAL";
            case "已禁用" -> "DISABLED";
            case "已锁定" -> "LOCKED";
            default -> status;
        };
    }

    private String accountStatusLabel(String status) {
        return switch (status) {
            case "NORMAL" -> "正常";
            case "DISABLED" -> "已禁用";
            case "LOCKED" -> "已锁定";
            case "DELETED" -> "已删除";
            default -> status;
        };
    }

    private String resourceStatusLabel(String status) {
        return switch (status) {
            case "PENDING_REVIEW" -> "待审核";
            case "REJECTED" -> "已驳回";
            case "PUBLISHED" -> "已发布";
            case "OFFLINE" -> "已下架";
            case "COPYRIGHT_DOWN" -> "版权下架";
            case "REVIEWING_RISK" -> "风险复核";
            case "DELETED" -> "已删除";
            default -> status;
        };
    }

    private String requestStatusLabel(String status) {
        return switch (status) {
            case "ONGOING" -> "进行中";
            case "RESOLVED" -> "已解决";
            case "CANCELLED" -> "已取消";
            case "CLOSED" -> "已关闭";
            default -> status;
        };
    }

    private String commentStatusLabel(String status) {
        return switch (status) {
            case "ACTIVE" -> "正常";
            case "HIDDEN" -> "已隐藏";
            case "DELETED" -> "已删除";
            default -> status;
        };
    }

    private String reportStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "待处理";
            case "PROCESSING" -> "处理中";
            case "RESOLVED" -> "已处理";
            case "REJECTED" -> "已驳回";
            default -> status;
        };
    }

    private String appealStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "待审核";
            case "PROCESSING" -> "处理中";
            case "APPROVED" -> "已处理";
            case "REJECTED" -> "已驳回";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String enabledStatusLabel(String status) {
        return "ENABLED".equals(status) ? "启用" : "禁用";
    }

    private String targetLabel(String targetType) {
        return switch (targetType) {
            case "COMMENT" -> "违规评论";
            case "REQUEST_POST" -> "违规求助帖";
            case "REQUEST_REPLY" -> "违规回复";
            case "USER" -> "违规用户";
            default -> "违规资源";
        };
    }

    private String reportAction(String reportType, String targetType) {
        if ("COPYRIGHT".equals(reportType)) {
            return "copyright-down-resource";
        }
        return switch (targetType) {
            case "COMMENT" -> "delete-comment";
            case "REQUEST_POST" -> "close-request";
            case "REQUEST_REPLY" -> "delete-reply";
            case "USER" -> "disable-user";
            default -> "offline-resource";
        };
    }

    private int adminSize(Map<String, String> params, int fallback) {
        String requested = params == null ? null : values.firstNonBlank(params.get("size"), params.get("pageSize"));
        return Math.max(1, Math.min(100, values.intValue(requested, fallback)));
    }

    private boolean hasCategoryOnlyFilter(Map<String, String> params) {
        return values.blankToNull(params.get("level")) != null || values.blankToNull(params.get("parentId")) != null;
    }

    private CategoryFilter categoryFilter(Map<String, String> params) {
        StringBuilder where = new StringBuilder("WHERE c.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        String keyword = values.blankToNull(params == null ? null : params.get("keyword"));
        String status = values.blankToNull(params == null ? null : params.get("status"));
        String level = values.blankToNull(params == null ? null : params.get("level"));
        Long parentId = values.longValue(params == null ? null : params.get("parentId"), null);
        if (keyword != null) {
            where.append(" AND c.category_name LIKE ?");
            args.add("%" + keyword + "%");
        }
        if (status != null) {
            where.append(" AND c.status = ?");
            args.add(enabledStatusValue(status));
        }
        if ("1".equals(level) || "2".equals(level)) {
            where.append(" AND c.level_no = ?");
            args.add(Integer.parseInt(level));
        }
        if (parentId != null) {
            where.append(" AND c.parent_id = ?");
            args.add(parentId);
        }
        return new CategoryFilter(where.toString(), args);
    }

    private TagFilter tagFilter(Map<String, String> params) {
        StringBuilder where = new StringBuilder("WHERE t.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        String keyword = values.blankToNull(params == null ? null : params.get("keyword"));
        String status = values.blankToNull(params == null ? null : params.get("status"));
        if (keyword != null) {
            where.append(" AND t.tag_name LIKE ?");
            args.add("%" + keyword + "%");
        }
        if (status != null) {
            where.append(" AND t.status = ?");
            args.add(enabledStatusValue(status));
        }
        return new TagFilter(where.toString(), args);
    }

    private String enabledStatusValue(String status) {
        return switch (status) {
            case "启用", "ENABLED", "NORMAL" -> "ENABLED";
            case "禁用", "DISABLED" -> "DISABLED";
            default -> status;
        };
    }

    private record CategoryFilter(String where, List<Object> args) {
    }

    private record TagFilter(String where, List<Object> args) {
    }
}

package com.resourcesharing.forum.service.resource;

import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ResourceQueryService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;

    public ResourceQueryService(TxSupport txSupport, ValueSupport values, MappingSupport mappings) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
    }

    public PageResult<Map<String, Object>> listResources(Map<String, String> params, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(1, List.of(defaultResource()), page, size);
        }
        try {
            String keyword = values.blankToNull(params.get("keyword"));
            String category2 = values.firstNonBlank(params.get("categoryId"), params.get("category2"), params.get("cate2"));
            String category1 = values.firstNonBlank(params.get("category1"), params.get("cate1"));
            String type = values.blankToNull(params.get("resourceType"));
            if (type == null) {
                type = values.blankToNull(params.get("type"));
            }
            StringBuilder where = new StringBuilder("WHERE r.deleted_at IS NULL AND r.status = 'PUBLISHED'");
            List<Object> args = new ArrayList<>();
            if (keyword != null) {
                where.append("""
                         AND (
                            r.title LIKE ? OR r.summary LIKE ? OR r.description LIKE ?
                            OR EXISTS (
                                SELECT 1 FROM member_profile publisher
                                WHERE publisher.id = r.publisher_id AND publisher.nickname LIKE ?
                            )
                            OR EXISTS (
                                SELECT 1
                                FROM resource_tag_rel rtr
                                JOIN tag_info ti ON ti.id = rtr.tag_id
                                WHERE rtr.resource_id = r.id AND ti.tag_name LIKE ?
                            )
                        )
                        """);
                String like = "%" + keyword + "%";
                args.add(like);
                args.add(like);
                args.add(like);
                args.add(like);
                args.add(like);
            }
            if (!category2.isBlank()) {
                where.append(" AND r.category_id = ?");
                args.add(values.longValue(category2, 0L));
            } else if (!category1.isBlank()) {
                where.append(" AND r.category_id IN (SELECT id FROM resource_category WHERE parent_id = ? AND deleted_at IS NULL)");
                args.add(values.longValue(category1, 0L));
            }
            if (type != null) {
                where.append(" AND r.resource_type = ?");
                args.add(resourceType(type));
            }
            long total = jdbc.queryForObject("SELECT COUNT(*) FROM resource_info r " + where, Long.class, args.toArray());
            args.add((page - 1) * size);
            args.add(size);
            String orderBy = resourceOrderBy(values.firstNonBlank(params.get("sort"), "latest"));
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT r.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM resource_info r
                    JOIN member_profile mp ON mp.id = r.publisher_id
                    LEFT JOIN resource_category c2 ON c2.id = r.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    %s
                    ORDER BY %s
                    LIMIT ?, ?
                    """.formatted(where, orderBy), mappings.resourceMapper(accountId), args.toArray());
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(1, List.of(defaultResource()), page, size);
        }
    }

    public Map<String, Object> resourceDetail(Long resourceId, Long accountId) {
        return values.map(
                "resource", resource(resourceId, accountId),
                "comments", comments(resourceId, accountId, 1, 20).list()
        );
    }

    public PageResult<Map<String, Object>> adminListResources(Map<String, String> params, Long adminAccountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int page = values.page(params);
        int size = values.size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String status = values.firstNonBlank(params.get("status"), params.get("resourceStatus"));
        String keyword = values.blankToNull(params.get("keyword"));
        StringBuilder where = new StringBuilder("WHERE r.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (!status.isBlank()) {
            where.append(" AND r.status = ?");
            args.add(status);
        }
        if (keyword != null) {
            where.append(" AND (r.title LIKE ? OR r.summary LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM resource_info r " + where, Long.class, args.toArray());
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
                ORDER BY r.update_time DESC, r.id DESC
                LIMIT ?, ?
                """.formatted(where), mappings.resourceMapper(adminAccountId), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> resource(Long resourceId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return defaultResource();
        }
        try {
            return jdbc.queryForObject("""
                    SELECT r.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM resource_info r
                    JOIN member_profile mp ON mp.id = r.publisher_id
                    LEFT JOIN resource_category c2 ON c2.id = r.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    WHERE r.id = ? AND r.deleted_at IS NULL
                      AND (
                          r.status = 'PUBLISHED'
                          OR EXISTS (
                              SELECT 1
                              FROM member_profile current_mp
                              JOIN user_account current_ua ON current_ua.id = current_mp.account_id
                              WHERE current_mp.account_id = ?
                                AND current_mp.id = r.publisher_id
                                AND current_mp.deleted_at IS NULL
                                AND current_ua.status = 'NORMAL'
                                AND current_ua.deleted_at IS NULL
                                AND (current_ua.locked_until IS NULL OR current_ua.locked_until <= NOW(3))
                          )
                          OR EXISTS (
                              SELECT 1
                              FROM administrator_profile ap
                              JOIN user_account admin_ua ON admin_ua.id = ap.account_id
                              WHERE ap.account_id = ?
                                AND ap.deleted_at IS NULL
                                AND admin_ua.status = 'NORMAL'
                                AND admin_ua.deleted_at IS NULL
                                AND (admin_ua.locked_until IS NULL OR admin_ua.locked_until <= NOW(3))
                                AND admin_ua.role IN ('ADMIN', 'SUPER_ADMIN', 'AUDITOR')
                          )
                      )
                    """, mappings.resourceMapper(accountId), resourceId, accountId, accountId);
        } catch (EmptyResultDataAccessException ignored) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "resource does not exist or cannot be viewed");
        } catch (DataAccessException ignored) {
            return defaultResource();
        }
    }

    private PageResult<Map<String, Object>> comments(Long resourceId, Long accountId, int page, int size) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            long total = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM comment_info
                    WHERE target_type = 'RESOURCE' AND target_id = ? AND status = 'ACTIVE' AND parent_id IS NULL AND deleted_at IS NULL
                    """, Long.class, resourceId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT ci.id, ci.target_type, ci.target_id, ci.content, ci.create_time, ci.member_id, ci.parent_id, mp.nickname,
                           (SELECT COUNT(*) FROM user_interaction ui
                            WHERE ui.target_type = 'COMMENT' AND ui.target_id = ci.id
                              AND ui.action_type = 'LIKE' AND ui.status = 'ACTIVE' AND ui.deleted_at IS NULL) AS like_count
                    FROM comment_info ci
                    JOIN member_profile mp ON mp.id = ci.member_id
                    WHERE ci.target_type = 'RESOURCE' AND ci.target_id = ? AND ci.status = 'ACTIVE' AND ci.parent_id IS NULL AND ci.deleted_at IS NULL
                    ORDER BY ci.create_time DESC
                    LIMIT ?, ?
                    """, mappings.commentMapper(accountId), resourceId, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    private Map<String, Object> defaultResource() {
        return values.map(
                "id", 1L,
                "title", "2026考研政治历年真题完整版",
                "summary", "整理近年考研政治真题和答案解析，适合课程项目演示。",
                "description", "整理近年考研政治真题和答案解析，适合课程项目演示。",
                "detail", "该资源用于演示资源发布、审核、下载、收藏、点赞、评论评分等核心流程。",
                "categoryId", 11L,
                "category1", "1",
                "category2", "11",
                "resourceType", "DOCUMENT",
                "type", "文档",
                "status", "PUBLISHED",
                "author", "考研资料君",
                "downloads", 136,
                "downloadCount", 136,
                "favoriteCount", 0,
                "likeCount", 0,
                "commentCount", 0,
                "score", 4.8,
                "ratingCount", 1,
                "date", values.today(),
                "publishedAt", values.today(),
                "tags", List.of("考研", "政治", "真题"),
                "attachments", List.of(values.map("id", 1L, "name", "kaoyan-politics-2026.zip", "fileName", "kaoyan-politics-2026.zip", "size", "2.0 MB", "type", "ZIP", "downloads", 136)),
                "fileName", "kaoyan-politics-2026.zip",
                "fileSize", "2.0 MB",
                "liked", false,
                "favorited", false,
                "userRating", 0
        );
    }

    private String resourceType(String display) {
        return switch (values.firstNonBlank(display)) {
            case "软件" -> "SOFTWARE";
            case "源码" -> "SOURCE_CODE";
            case "素材" -> "MATERIAL";
            case "教程" -> "COURSE";
            case "模板" -> "TEMPLATE";
            case "链接" -> "LINK";
            default -> values.firstNonBlank(display).matches("[A-Z_]+") ? values.firstNonBlank(display) : "DOCUMENT";
        };
    }

    private String resourceOrderBy(String sort) {
        return switch (sort) {
            case "download" -> "r.download_count DESC, r.published_time DESC, r.id DESC";
            case "score" -> "r.average_rating DESC, r.rating_count DESC, r.published_time DESC, r.id DESC";
            default -> "r.published_time DESC, r.id DESC";
        };
    }
}

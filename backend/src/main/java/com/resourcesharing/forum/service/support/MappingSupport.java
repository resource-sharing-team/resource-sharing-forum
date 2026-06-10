package com.resourcesharing.forum.service.support;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class MappingSupport {
    private final ValueSupport values;
    private final ForumLookupService lookup;

    public MappingSupport(ValueSupport values, ForumLookupService lookup) {
        this.values = values;
        this.lookup = lookup;
    }

    public RowMapper<Map<String, Object>> userMapper() {
        return (rs, rowNum) -> {
            int currentPoints = rs.getInt("current_points");
            int frozenPoints = rs.getInt("frozen_points");
            int availablePoints = Math.max(0, currentPoints - frozenPoints);
            int levelMinPoints = rs.getInt("level_min_points");
            Object nextLevelMinObject = rs.getObject("next_level_min_points");
            Integer nextLevelMinPoints = nextLevelMinObject == null ? null : ((Number) nextLevelMinObject).intValue();
            int expNeeded = nextLevelMinPoints == null ? 0 : Math.max(0, nextLevelMinPoints - currentPoints);
            int upgradeProgress = nextLevelMinPoints == null
                    ? 100
                    : Math.max(0, Math.min(100, (int) Math.round((currentPoints - levelMinPoints) * 100.0 / Math.max(1, nextLevelMinPoints - levelMinPoints))));
            return values.map(
                    "id", rs.getLong("id"),
                    "memberId", rs.getLong("member_id"),
                    "username", rs.getString("username"),
                    "nickname", firstNonBlank(rs.getString("nickname"), rs.getString("username")),
                    "email", rs.getString("email"),
                    "role", roleForToken(rs.getString("role")),
                    "status", "NORMAL",
                    "emailVerified", true,
                    "bio", firstNonBlank(rs.getString("bio"), ""),
                    "contact", rs.getString("email"),
                    "avatar", firstNonBlank(rs.getString("avatar_url"), ""),
                    "levelCode", firstNonBlank(rs.getString("level_code"), "NORMAL"),
                    "level", firstNonBlank(rs.getString("level_name"), "Member"),
                    "levelMinPoints", levelMinPoints,
                    "levelMaxPoints", rs.getObject("level_max_points"),
                    "nextLevel", firstNonBlank(rs.getString("next_level_name"), ""),
                    "nextLevelMinPoints", nextLevelMinPoints == null ? 0 : nextLevelMinPoints,
                    "points", currentPoints,
                    "frozenPoints", frozenPoints,
                    "availablePoints", availablePoints,
                    "rewardLimit", rs.getInt("reward_limit"),
                    "dailyDownloadLimit", rs.getInt("daily_download_limit"),
                    "dailyResourcePublishLimit", rs.getInt("daily_resource_publish_limit"),
                    "dailyRequestPublishLimit", rs.getInt("daily_request_publish_limit"),
                    "maxFilesPerResource", rs.getInt("max_files_per_resource"),
                    "maxFileSizeMb", rs.getInt("max_file_size_mb"),
                    "canApplyTop", rs.getInt("can_apply_top") == 1,
                    "expNeeded", expNeeded,
                    "upgradeProgress", upgradeProgress,
                    "benefits", memberBenefits(
                            rs.getInt("daily_download_limit"),
                            rs.getInt("daily_resource_publish_limit"),
                            rs.getInt("daily_request_publish_limit"),
                            rs.getInt("max_files_per_resource"),
                            rs.getInt("max_file_size_mb"),
                            rs.getInt("reward_limit"),
                            rs.getInt("can_apply_top") == 1
                    ),
                    "passwordUpdatedAt", values.date(rs.getObject("password_changed_time", java.time.LocalDateTime.class))
            );
        };
    }

    public RowMapper<Map<String, Object>> resourceMapper(Long accountId) {
        return (rs, rowNum) -> {
            Long id = rs.getLong("id");
            Long memberId = lookup.memberId(accountId);
            List<Map<String, Object>> attachments = lookup.attachments(id);
            return values.map(
                    "id", id,
                    "title", rs.getString("title"),
                    "summary", rs.getString("summary"),
                    "description", rs.getString("summary"),
                    "detail", rs.getString("description"),
                    "categoryId", rs.getObject("category2_id"),
                    "category1", values.stringId(rs.getObject("category1_id")),
                    "category2", values.stringId(rs.getObject("category2_id")),
                    "resourceType", rs.getString("resource_type"),
                    "type", displayResourceType(rs.getString("resource_type")),
                    "status", rs.getString("status"),
                    "author", rs.getString("author_name"),
                    "downloads", rs.getInt("download_count"),
                    "downloadCount", rs.getInt("download_count"),
                    "favoriteCount", rs.getInt("favorite_count"),
                    "likeCount", rs.getInt("like_count"),
                    "commentCount", rs.getInt("comment_count"),
                    "score", rs.getBigDecimal("average_rating") == null ? 0 : rs.getBigDecimal("average_rating").doubleValue(),
                    "ratingCount", rs.getInt("rating_count"),
                    "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class)),
                    "publishedAt", values.date(rs.getObject("published_time", java.time.LocalDateTime.class)),
                    "tags", lookup.resourceTags(id),
                    "attachments", attachments,
                    "fileName", attachments.isEmpty() ? "" : attachments.get(0).get("name"),
                    "fileSize", attachments.isEmpty() ? "" : attachments.get(0).get("size"),
                    "liked", lookup.interactionActive(memberId, "RESOURCE", id, "LIKE"),
                    "favorited", lookup.interactionActive(memberId, "RESOURCE", id, "FAVORITE"),
                    "userRating", lookup.userRating(memberId, id)
            );
        };
    }

    public RowMapper<Map<String, Object>> requestMapper() {
        return (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "title", rs.getString("title"),
                "content", rs.getString("content"),
                "description", rs.getString("content"),
                "categoryId", rs.getObject("category2_id"),
                "category1", values.stringId(rs.getObject("category1_id")),
                "category2", values.stringId(rs.getObject("category2_id")),
                "rewardType", rs.getInt("reward_points") > 0 ? "POINT" : "FREE",
                "rewardPoints", rs.getInt("reward_points"),
                "rewardStatus", firstNonBlank(rs.getString("reward_status"), rs.getInt("reward_points") > 0 ? "FROZEN" : "NONE"),
                "points", rs.getInt("reward_points"),
                "replyCount", rs.getInt("answer_count"),
                "commentCount", rs.getInt("comment_count"),
                "author", rs.getString("author_name"),
                "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class)),
                "status", rs.getString("status"),
                "tags", lookup.requestTags(rs.getLong("id")),
                "expectedFormat", firstNonBlank(rs.getString("expected_format"), "unlimited"),
                "format", firstNonBlank(rs.getString("expected_format"), "unlimited")
        );
    }

    public RowMapper<Map<String, Object>> replyMapper() {
        return (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "requestId", rs.getLong("request_id"),
                "author", rs.getString("author_name"),
                "content", rs.getString("content"),
                "resourceId", rs.getObject("resource_id"),
                "externalUrl", rs.getString("external_url"),
                "accepted", rs.getInt("is_accepted") == 1,
                "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class))
        );
    }

    public RowMapper<Map<String, Object>> commentMapper(Long accountId) {
        return (rs, rowNum) -> {
            Long memberId = lookup.memberId(accountId);
            return values.map(
                    "id", rs.getLong("id"),
                    "targetType", rs.getString("target_type"),
                    "targetId", rs.getLong("target_id"),
                    "parentId", rs.getObject("parent_id"),
                    "author", rs.getString("nickname"),
                    "content", rs.getString("content"),
                    "date", values.date(rs.getObject("created_at", java.time.LocalDateTime.class)),
                    "mine", Objects.equals(rs.getLong("member_id"), memberId),
                    "likeCount", rs.getInt("like_count"),
                    "liked", lookup.interactionActive(memberId, "COMMENT", rs.getLong("id"), "LIKE"),
                    "replies", List.of()
            );
        };
    }

    private static String displayResourceType(String type) {
        return switch (type == null ? "" : type.trim()) {
            case "SOFTWARE" -> "软件";
            case "SOURCE_CODE" -> "源码";
            case "MATERIAL" -> "素材";
            case "COURSE" -> "教程";
            case "TEMPLATE" -> "模板";
            case "LINK" -> "链接";
            default -> "文档";
        };
    }

    private List<Map<String, Object>> memberBenefits(
            int dailyDownloadLimit,
            int dailyResourcePublishLimit,
            int dailyRequestPublishLimit,
            int maxFilesPerResource,
            int maxFileSizeMb,
            int rewardLimit,
            boolean canApplyTop
    ) {
        return List.of(
                values.map("name", "每日下载次数", "description", "每天可成功下载的资源附件次数", "limit", dailyDownloadLimit, "enabled", dailyDownloadLimit > 0),
                values.map("name", "每日发布资源", "description", "每天可发布的资源数量", "limit", dailyResourcePublishLimit, "enabled", dailyResourcePublishLimit > 0),
                values.map("name", "每日发布求资源", "description", "每天可发布的求资源帖子数量", "limit", dailyRequestPublishLimit, "enabled", dailyRequestPublishLimit > 0),
                values.map("name", "单资源附件数", "description", "单个资源最多可上传的附件数量", "limit", maxFilesPerResource, "enabled", maxFilesPerResource > 0),
                values.map("name", "单附件大小", "description", "单个附件最大体积，单位 MB", "limit", maxFileSizeMb, "enabled", maxFileSizeMb > 0),
                values.map("name", "单帖悬赏上限", "description", "单个求资源帖子可设置的最高悬赏积分", "limit", rewardLimit, "enabled", rewardLimit > 0),
                values.map("name", "申请置顶", "description", "当前等级是否允许申请资源置顶", "limit", canApplyTop ? "允许" : "不允许", "enabled", canApplyTop)
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String roleForToken(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || "AUDITOR".equals(role) ? "ADMIN" : "MEMBER";
    }
}

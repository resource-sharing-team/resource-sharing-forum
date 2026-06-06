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
        return (rs, rowNum) -> values.map(
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
                "level", firstNonBlank(rs.getString("level_name"), "Member"),
                "points", rs.getInt("current_points"),
                "frozenPoints", rs.getInt("frozen_points"),
                "expNeeded", Math.max(0, 1000 - rs.getInt("current_points")),
                "passwordUpdatedAt", values.date(rs.getObject("password_changed_time", java.time.LocalDateTime.class))
        );
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
                    "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class)),
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
                "rewardPoints", rs.getInt("reward_points"),
                "points", rs.getInt("reward_points"),
                "replyCount", rs.getInt("answer_count"),
                "commentCount", rs.getInt("comment_count"),
                "author", rs.getString("author_name"),
                "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class)),
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
                "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class))
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
                    "date", values.date(rs.getObject("create_time", java.time.LocalDateTime.class)),
                    "mine", Objects.equals(rs.getLong("member_id"), memberId),
                    "replies", List.of()
            );
        };
    }

    private static String displayResourceType(String type) {
        return switch (type == null ? "" : type.trim()) {
            case "SOFTWARE" -> "software";
            case "SOURCE_CODE" -> "source";
            case "MATERIAL" -> "material";
            case "COURSE" -> "course";
            case "TEMPLATE" -> "template";
            case "LINK" -> "link";
            default -> "document";
        };
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

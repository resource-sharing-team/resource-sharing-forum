package com.resourcesharing.forum.service.resource;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.domain.statemachine.ResourceStateMachine;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.point.PointRuleService;
import com.resourcesharing.forum.service.support.ContentModerationService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import com.resourcesharing.forum.service.system.AdminLogService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service("designSpecResourceLifecycleService")
public class ResourceService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final ResourceQueryService resourceQueryService;
    private final AdminLogService adminLogService;
    private final NotificationDispatcher notificationDispatcher;
    private final ContentModerationService contentModerationService;
    private final PointManager pointManager;
    private final PointRuleService pointRules;

    public ResourceService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            ResourceQueryService resourceQueryService,
            AdminLogService adminLogService,
            NotificationDispatcher notificationDispatcher,
            ContentModerationService contentModerationService,
            PointManager pointManager,
            PointRuleService pointRules
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.resourceQueryService = resourceQueryService;
        this.adminLogService = adminLogService;
        this.notificationDispatcher = notificationDispatcher;
        this.contentModerationService = contentModerationService;
        this.pointManager = pointManager;
        this.pointRules = pointRules;
    }

    public Map<String, Object> publishResource(Long accountId, Map<String, Object> request, List<MultipartFile> files) {
        Long categoryId = values.number(values.firstPresent(request, "categoryId", "category2"), 0L);
        String title = normalizeResourceTitle(values.value(request, "title", ""));
        String detail = normalizeResourceDescription(values.firstNonBlank(values.value(request, "detail", ""), values.value(request, "description", "")));
        String summary = normalizeResourceSummary(values.value(request, "summary", ""), detail);
        List<String> tags = normalizeTags(values.firstPresent(request, "tags"));
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            Map<String, Object> resource = resourceQueryService.resource(1L, accountId);
            resource.put("status", "PENDING_REVIEW");
            return resource;
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            ensureResourcePublishBenefits(jdbc, memberId, files);
            ensurePublishableCategory(jdbc, categoryId);
            ensureTagsUsable(jdbc, tags);
            String type = resourceType(values.firstNonBlank(values.value(request, "resourceType", ""), values.value(request, "type", "DOCUMENT")));
            boolean draft = Boolean.parseBoolean(values.value(request, "draft", "false")) || "DRAFT".equalsIgnoreCase(values.value(request, "status", ""));
            String initialStatus = draft ? "DRAFT" : "PENDING_REVIEW";
            KeyHolder resourceKey = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO resource_info(
                            publisher_id, category_id, title, resource_type, summary, description,
                            external_url, status, submitted_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, IF(? = 'PENDING_REVIEW', NOW(3), NULL))
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, memberId);
                statement.setLong(2, categoryId);
                statement.setString(3, title);
                statement.setString(4, type);
                statement.setString(5, summary);
                statement.setString(6, detail);
                statement.setString(7, values.blankToNull(values.value(request, "externalUrl", "")));
                statement.setString(8, initialStatus);
                statement.setString(9, initialStatus);
                return statement;
            }, resourceKey);
            Long resourceId = values.key(resourceKey);
            insertResourceTags(jdbc, resourceId, tags);
            insertAttachments(jdbc, "RESOURCE", resourceId, accountId, files, values.firstNonBlank(values.value(request, "fileName", ""), "uploaded-file.zip"));
            jdbc.update("""
                    INSERT INTO resource_version(resource_id, version_no, title, summary, description, category_id, resource_type, submitter_id)
                    VALUES (?, 1, ?, ?, ?, ?, ?, ?)
                    """, resourceId, title, summary, detail, categoryId, type, memberId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, NULL, ?, ?, ?)
                    """, resourceId, initialStatus, accountId, draft ? "Saved as draft" : "Submitted for review");
            incrementDailyPublishCount(jdbc, memberId);
            return resourceQueryService.resource(resourceId, accountId);
        });
    }

    public void deleteResource(Long resourceId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return;
        }
        txSupport.required(() -> {
            if (accountId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Please sign in before deleting resources");
            }
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT r.status, r.publisher_id, ua.role
                    FROM resource_info r
                    JOIN user_account ua ON ua.id = ?
                    WHERE r.id = ? AND r.deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "status", rs.getString("status"),
                    "publisherId", rs.getLong("publisher_id"),
                    "role", rs.getString("role")
            ), accountId, resourceId);
            boolean admin = List.of("ADMIN", "SUPER_ADMIN", "AUDITOR").contains(String.valueOf(before.get("role")));
            Long memberId = admin ? null : lookup.requireMemberId(accountId);
            if (!admin && !Objects.equals(values.number(before.get("publisherId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "No permission to delete this resource");
            }
            String reason = admin ? "Admin deleted resource" : "Publisher deleted resource";
            ResourceStateMachine.assertCanTransit(String.valueOf(before.get("status")), "DELETED", "DELETE", admin ? "ADMIN" : "MEMBER", !admin, reason);
            jdbc.update("UPDATE resource_info SET status = 'DELETED', deleted_at = NOW(3) WHERE id = ?", resourceId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'DELETED', ?, ?)
                    """, resourceId, before.get("status"), accountId, reason);
            if (admin) {
                adminLogService.record(lookup.adminProfileId(accountId), "RESOURCE_DELETED", "RESOURCE", resourceId, String.valueOf(before.get("status")), "DELETED");
            }
            return null;
        });
    }

    public Map<String, Object> auditResource(Long resourceId, Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", resourceId, "status", "PUBLISHED", "auditResult", "APPROVED");
        }
        String action = values.value(request, "action", values.value(request, "auditResult", "APPROVE")).toUpperCase();
        if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
            return transitionResourceByAdmin(resourceId, adminAccountId, "APPROVE", request);
        }
        if ("REJECT".equals(action) || "REJECTED".equals(action)) {
            return transitionResourceByAdmin(resourceId, adminAccountId, "REJECT", request);
        }
        return transitionResourceByAdmin(resourceId, adminAccountId, action, request);
    }

    public Map<String, Object> transitionResourceByAdmin(Long resourceId, Long adminAccountId, String action, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", resourceId, "status", "PUBLISHED");
        }
        return txSupport.required(() -> {
            String normalized = values.firstNonBlank(action, values.value(request, "action", "")).toUpperCase();
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT id, publisher_id, status, current_version_no, title
                    FROM resource_info
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "publisherId", rs.getLong("publisher_id"),
                    "status", rs.getString("status"),
                    "version", rs.getInt("current_version_no"),
                    "title", rs.getString("title")
            ), resourceId);
            String from = String.valueOf(before.get("status"));
            String to = ResourceStateMachine.targetStatusForAction(normalized);
            String auditResult = auditResultForAction(normalized, to);
            String reason = values.firstNonBlank(values.value(request, "reason", ""), defaultResourceReason(normalized));
            ResourceStateMachine.assertCanTransit(from, to, normalized, "ADMIN", false, reason);
            jdbc.update("""
                    UPDATE resource_info
                    SET status = ?,
                        published_time = IF(? = 'PUBLISHED', COALESCE(published_time, NOW(3)), published_time),
                        offline_time = IF(? IN ('OFFLINE', 'COPYRIGHT_DOWN'), NOW(3), offline_time),
                        reject_reason = IF(? = 'REJECTED', ?, reject_reason),
                        deleted_at = IF(? = 'DELETED', NOW(3), deleted_at)
                    WHERE id = ?
                    """, to, to, to, to, reason, to, resourceId);
            Long adminProfileId = lookup.adminProfileId(adminAccountId);
            jdbc.update("""
                    INSERT INTO resource_audit_record(resource_id, version_no, auditor_id, audit_result, reason)
                    VALUES (?, ?, ?, ?, ?)
                    """, resourceId, values.number(before.get("version"), 1L), adminProfileId, auditResult, reason);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, ?, ?, ?)
                    """, resourceId, from, to, adminAccountId, reason);
            adminLogService.record(adminProfileId, "RESOURCE_" + auditResult, "RESOURCE", resourceId, from, to);
            applyResourcePointRule(values.number(before.get("publisherId"), 0L), resourceId, adminAccountId, normalized, from, to, reason);
            notificationDispatcher.dispatchToMember(
                    values.number(before.get("publisherId"), 0L),
                    "RESOURCE_STATUS",
                    "Resource status updated",
                    "Resource \"" + before.get("title") + "\" status changed to " + to + ". Reason: " + reason,
                    "RESOURCE",
                    resourceId
            );
            return resourceQueryService.resource(resourceId, adminAccountId);
        });
    }

    public Map<String, Object> submitResource(Long resourceId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return resourceQueryService.resource(resourceId, accountId);
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT status FROM resource_info
                    WHERE id = ? AND publisher_id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map("status", rs.getString("status")), resourceId, memberId);
            String from = String.valueOf(before.get("status"));
            ResourceStateMachine.assertCanTransit(from, "PENDING_REVIEW", "SUBMIT", "MEMBER", true, "Publisher submitted for review");
            jdbc.update("UPDATE resource_info SET status = 'PENDING_REVIEW', submitted_time = NOW(3) WHERE id = ?", resourceId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'PENDING_REVIEW', ?, 'Publisher submitted for review')
                    """, resourceId, from, accountId);
            return resourceQueryService.resource(resourceId, accountId);
        });
    }

    public Map<String, Object> withdrawResource(Long resourceId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return resourceQueryService.resource(resourceId, accountId);
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT status FROM resource_info
                    WHERE id = ? AND publisher_id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map("status", rs.getString("status")), resourceId, memberId);
            String from = String.valueOf(before.get("status"));
            String reason = values.firstNonBlank(values.value(request, "reason", ""), "Publisher withdrew for editing");
            ResourceStateMachine.assertCanTransit(from, "DRAFT", "WITHDRAW", "MEMBER", true, reason);
            jdbc.update("UPDATE resource_info SET status = 'DRAFT' WHERE id = ?", resourceId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'DRAFT', ?, ?)
                    """, resourceId, from, accountId, reason);
            return resourceQueryService.resource(resourceId, accountId);
        });
    }

    private void insertResourceTags(JdbcTemplate jdbc, Long resourceId, List<String> tags) {
        for (String tag : tags) {
            Long tagId = ensureTag(jdbc, tag);
            jdbc.update("INSERT IGNORE INTO resource_tag_rel(resource_id, tag_id) VALUES (?, ?)", resourceId, tagId);
        }
    }

    private Long ensureTag(JdbcTemplate jdbc, String tagName) {
        try {
            Map<String, Object> tag = jdbc.queryForObject("""
                    SELECT id, status
                    FROM tag_info
                    WHERE tag_name = ? AND deleted_at IS NULL
                    """, (rs, rowNum) -> values.map("id", rs.getLong("id"), "status", rs.getString("status")), tagName);
            if (!"ENABLED".equals(tag.get("status"))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "disabled tag cannot be used");
            }
            return values.number(tag.get("id"), 0L);
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

    private String normalizeResourceTitle(String title) {
        String normalized = values.firstNonBlank(title);
        if (normalized.length() < 5 || normalized.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource title length must be between 5 and 100");
        }
        contentModerationService.requireClean(normalized);
        return normalized;
    }

    private String normalizeResourceDescription(String description) {
        String normalized = values.firstNonBlank(description);
        if (normalized.length() < 20 || normalized.length() > 5000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource description length must be between 20 and 5000");
        }
        contentModerationService.requireClean(normalized);
        return normalized;
    }

    private String normalizeResourceSummary(String summary, String detail) {
        String normalized = values.firstNonBlank(summary);
        if (normalized.isBlank()) {
            return detail.length() <= 300 ? detail : detail.substring(0, 300);
        }
        if (normalized.length() > 300) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource summary length must be at most 300");
        }
        contentModerationService.requireClean(normalized);
        return normalized;
    }

    private List<String> normalizeTags(Object rawTags) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawTags instanceof Iterable<?> items) {
            for (Object item : items) {
                addTag(normalized, item == null ? "" : String.valueOf(item));
            }
        } else if (rawTags != null && !String.valueOf(rawTags).isBlank()) {
            for (String tag : String.valueOf(rawTags).split("[,\\uFF0C]")) {
                addTag(normalized, tag);
            }
        }
        if (normalized.isEmpty() || normalized.size() > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource tags count must be between 1 and 5");
        }
        return new ArrayList<>(normalized);
    }

    private void addTag(Set<String> tags, String tag) {
        String normalized = values.firstNonBlank(tag);
        if (normalized.isBlank()) {
            return;
        }
        if (normalized.length() > 12) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource tag length must be at most 12");
        }
        tags.add(normalized);
    }

    private void ensurePublishableCategory(JdbcTemplate jdbc, Long categoryId) {
        if (categoryId == null || categoryId == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource category is required");
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM resource_category
                WHERE id = ? AND level_no = 2 AND status = 'ENABLED' AND deleted_at IS NULL
                """, Integer.class, categoryId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resource category must be an enabled second-level category");
        }
    }

    private void ensureTagsUsable(JdbcTemplate jdbc, List<String> tags) {
        for (String tag : tags) {
            try {
                String status = jdbc.queryForObject("""
                        SELECT status
                        FROM tag_info
                        WHERE tag_name = ? AND deleted_at IS NULL
                        """, String.class, tag);
                if (!"ENABLED".equals(status)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "disabled tag cannot be used");
                }
            } catch (DataAccessException ignored) {
                // New tags are created during relation insertion.
            }
        }
    }

    private void ensureResourcePublishBenefits(JdbcTemplate jdbc, Long memberId, List<MultipartFile> files) {
        Map<String, Object> benefits;
        try {
            benefits = memberBenefits(jdbc, memberId);
        } catch (DataAccessException ignored) {
            return;
        }
        int dailyLimit = Math.max(0, (int) values.number(benefits.get("dailyResourcePublishLimit"), 0L).longValue());
        if (dailyLimit <= 0) {
            dailyLimit = pointRules.resourceDailyPublishLimit();
        }
        if (dailyLimit > 0) {
            Integer publishedToday = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM resource_info
                    WHERE publisher_id = ?
                      AND deleted_at IS NULL
                      AND DATE(created_at) = CURRENT_DATE()
                    """, Integer.class, memberId);
            if (publishedToday != null && publishedToday >= dailyLimit) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Daily resource publish limit has been reached");
            }
        }
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        int maxFiles = Math.max(1, (int) values.number(benefits.get("maxFilesPerResource"), 1L).longValue());
        if (safeFiles.size() > maxFiles) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Resource attachment count exceeds member level limit");
        }
        long maxBytes = Math.max(1, values.number(benefits.get("maxFileSizeMb"), 100L)) * 1024L * 1024L;
        for (MultipartFile file : safeFiles) {
            if (file.getSize() > maxBytes) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Resource attachment size exceeds member level limit");
            }
        }
    }

    private Map<String, Object> memberBenefits(JdbcTemplate jdbc, Long memberId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(ml.daily_resource_publish_limit, 0) AS daily_resource_publish_limit,
                       COALESCE(ml.max_files_per_resource, 1) AS max_files_per_resource,
                       COALESCE(ml.max_file_size_mb, 100) AS max_file_size_mb
                FROM member_point_account mpa
                LEFT JOIN membership_level ml ON ml.id = mpa.level_id AND ml.deleted_at IS NULL
                WHERE mpa.member_id = ? AND mpa.deleted_at IS NULL
                """, (rs, rowNum) -> values.map(
                "dailyResourcePublishLimit", rs.getInt("daily_resource_publish_limit"),
                "maxFilesPerResource", rs.getInt("max_files_per_resource"),
                "maxFileSizeMb", rs.getInt("max_file_size_mb")
        ), memberId);
    }

    private void incrementDailyPublishCount(JdbcTemplate jdbc, Long memberId) {
        jdbc.update("""
                INSERT INTO member_daily_stat(stat_date, member_id, publish_count)
                VALUES (CURRENT_DATE(), ?, 1)
                ON DUPLICATE KEY UPDATE publish_count = publish_count + 1
                """, memberId);
    }

    private void applyResourcePointRule(Long publisherId, Long resourceId, Long adminAccountId, String action, String from, String to, String reason) {
        if (publisherId == null || publisherId == 0) {
            return;
        }
        if ("PUBLISHED".equals(to)) {
            pointManager.earn(publisherId, pointRules.resourceApprovedPoints(), "RESOURCE_APPROVED", "RESOURCE",
                    resourceId, adminAccountId, "Resource approved reward", "resource-approved:" + resourceId);
            return;
        }
        if ("OFFLINE".equals(to) || "COPYRIGHT_DOWN".equals(to)) {
            pointManager.deduct(publisherId, pointRules.resourceOfflinePenalty(), "RESOURCE_OFFLINE_PENALTY", "RESOURCE",
                    resourceId, adminAccountId, values.firstNonBlank(reason, "Resource violation penalty"),
                    "resource-offline-penalty:" + to + ":" + resourceId);
        }
    }

    private void insertAttachments(JdbcTemplate jdbc, String ownerType, Long ownerId, Long accountId, List<MultipartFile> files, String fallbackName) {
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (safeFiles.isEmpty()) {
            insertAttachment(jdbc, ownerType, ownerId, accountId, fallbackName, "application/octet-stream", 0);
            return;
        }
        for (MultipartFile file : safeFiles) {
            insertAttachment(jdbc, ownerType, ownerId, accountId, values.firstNonBlank(file.getOriginalFilename(), "uploaded-file"), file.getContentType(), file.getSize());
        }
    }

    private void insertAttachment(JdbcTemplate jdbc, String ownerType, Long ownerId, Long accountId, String fileName, String contentType, long size) {
        jdbc.update("""
                INSERT INTO file_attachment(owner_type, owner_id, uploader_id, original_file_name, stored_file_name, file_ext, mime_type, file_size, storage_path, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NORMAL')
                """, ownerType, ownerId, accountId, fileName, ownerType.toLowerCase() + "-" + ownerId + "-" + fileName,
                fileExt(fileName), contentType, size, "./uploads/" + ownerType.toLowerCase() + "/" + ownerId + "/" + fileName);
    }

    private String auditResultForAction(String action, String targetStatus) {
        return switch (targetStatus) {
            case "PUBLISHED" -> "PUBLISHED".equals(action) || "APPROVE".equals(action) || "APPROVED".equals(action) ? "APPROVED" : "RESTORED";
            case "REJECTED" -> "REJECTED";
            case "REVIEWING_RISK" -> "RISK_REVIEW";
            case "COPYRIGHT_DOWN" -> "COPYRIGHT_DOWN";
            case "DELETED" -> "DELETED";
            default -> "OFFLINE";
        };
    }

    private String defaultResourceReason(String action) {
        return switch (values.firstNonBlank(action)) {
            case "APPROVE", "APPROVED" -> "Review approved";
            case "REJECT", "REJECTED" -> "Review rejected";
            case "RISK", "RISK_REVIEW", "REVIEWING_RISK" -> "Entered risk review";
            case "COPYRIGHT", "COPYRIGHT_DOWN" -> "Taken down for copyright complaint";
            case "RESTORE", "RESTORED", "RISK_CLEAR", "COPYRIGHT_CLEAR" -> "Restored after review";
            case "DELETE", "DELETED" -> "Admin deleted resource";
            default -> "Admin took resource offline";
        };
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

    private String fileExt(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "file";
    }
}

package com.resourcesharing.forum.service.resource;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.domain.statemachine.ResourceStateMachine;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service("designSpecResourceLifecycleService")
public class ResourceService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final ResourceQueryService resourceQueryService;
    private final AdminLogService adminLogService;
    private final NotificationDispatcher notificationDispatcher;

    public ResourceService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            ResourceQueryService resourceQueryService,
            AdminLogService adminLogService,
            NotificationDispatcher notificationDispatcher
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.resourceQueryService = resourceQueryService;
        this.adminLogService = adminLogService;
        this.notificationDispatcher = notificationDispatcher;
    }

    public Map<String, Object> publishResource(Long accountId, Map<String, Object> request, List<MultipartFile> files) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            Map<String, Object> resource = resourceQueryService.resource(1L, accountId);
            resource.put("status", "PENDING_REVIEW");
            return resource;
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Long categoryId = values.number(values.firstPresent(request, "categoryId", "category2"), 11L);
            String title = values.firstNonBlank(values.value(request, "title", ""), "New Resource");
            String summary = values.firstNonBlank(values.value(request, "summary", ""), values.value(request, "description", ""), "Resource summary");
            String detail = values.firstNonBlank(values.value(request, "detail", ""), values.value(request, "description", ""), "Resource detail");
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
            insertResourceTags(jdbc, resourceId, values.value(request, "tags", ""));
            insertAttachments(jdbc, "RESOURCE", resourceId, accountId, files, values.firstNonBlank(values.value(request, "fileName", ""), "uploaded-file.zip"));
            jdbc.update("""
                    INSERT INTO resource_version(resource_id, version_no, title, summary, description, category_id, resource_type, submitter_id)
                    VALUES (?, 1, ?, ?, ?, ?, ?, ?)
                    """, resourceId, title, summary, detail, categoryId, type, memberId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, NULL, ?, ?, ?)
                    """, resourceId, initialStatus, accountId, draft ? "Saved as draft" : "Submitted for review");
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

    private void insertResourceTags(JdbcTemplate jdbc, Long resourceId, String tagsText) {
        for (String tag : values.splitTags(tagsText)) {
            Long tagId = ensureTag(jdbc, tag);
            jdbc.update("INSERT IGNORE INTO resource_tag_rel(resource_id, tag_id) VALUES (?, ?)", resourceId, tagId);
        }
    }

    private Long ensureTag(JdbcTemplate jdbc, String tagName) {
        try {
            return jdbc.queryForObject("SELECT id FROM tag_info WHERE tag_name = ?", Long.class, tagName);
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

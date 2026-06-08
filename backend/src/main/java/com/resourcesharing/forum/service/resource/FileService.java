package com.resourcesharing.forum.service.resource;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

@Service("designSpecResourceFileService")
public class FileService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;

    public FileService(TxSupport txSupport, ValueSupport values, ForumLookupService lookup) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
    }

    public Map<String, Object> downloadAttachment(Long attachmentId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("recordId", 1L, "fileName", "demo.zip", "downloadUrl", "/api/v1/attachments/" + attachmentId + "/stream");
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Map<String, Object> attachment = jdbc.queryForObject("""
                    SELECT fa.id, fa.original_file_name, fa.owner_id AS resource_id, r.status
                    FROM file_attachment fa
                    JOIN resource_info r ON r.id = fa.owner_id AND fa.owner_type = 'RESOURCE'
                    WHERE fa.id = ? AND fa.status = 'NORMAL' AND fa.deleted_at IS NULL
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "fileName", rs.getString("original_file_name"),
                    "resourceId", rs.getLong("resource_id"),
                    "status", rs.getString("status")
            ), attachmentId);
            if (!"PUBLISHED".equals(attachment.get("status"))) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Resource is not published");
            }
            Long resourceId = values.number(attachment.get("resourceId"), 0L);
            Integer previous = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM download_record
                    WHERE member_id = ? AND resource_id = ? AND status = 'SUCCESS'
                    """, Integer.class, memberId, resourceId);
            boolean first = previous == null || previous == 0;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO download_record(member_id, resource_id, attachment_id, file_name, status, is_first_success)
                        VALUES (?, ?, ?, ?, 'SUCCESS', ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, memberId);
                statement.setLong(2, resourceId);
                statement.setLong(3, attachmentId);
                statement.setString(4, String.valueOf(attachment.get("fileName")));
                statement.setInt(5, first ? 1 : 0);
                return statement;
            }, keyHolder);
            jdbc.update("UPDATE file_attachment SET download_count = download_count + 1 WHERE id = ?", attachmentId);
            if (first) {
                jdbc.update("UPDATE resource_info SET download_count = download_count + 1 WHERE id = ?", resourceId);
            }
            return values.map("recordId", values.key(keyHolder), "fileName", attachment.get("fileName"), "downloadUrl", "/api/v1/attachments/" + attachmentId + "/stream");
        });
    }

    public Map<String, Object> downloadResource(Long resourceId, Long attachmentId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return downloadAttachment(attachmentId == null || attachmentId == 0 ? resourceId : attachmentId, accountId);
        }
        Long resolvedAttachmentId = attachmentId;
        if (resolvedAttachmentId == null || resolvedAttachmentId == 0) {
            try {
                resolvedAttachmentId = jdbc.queryForObject("""
                        SELECT id
                        FROM file_attachment
                        WHERE owner_type = 'RESOURCE' AND owner_id = ? AND status = 'NORMAL' AND deleted_at IS NULL
                        ORDER BY id ASC
                        LIMIT 1
                        """, Long.class, resourceId);
            } catch (Exception ignored) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "resource has no downloadable attachment");
            }
        }
        Long ownerId;
        try {
            ownerId = jdbc.queryForObject("""
                    SELECT owner_id
                    FROM file_attachment
                    WHERE id = ? AND owner_type = 'RESOURCE' AND status = 'NORMAL' AND deleted_at IS NULL
                    """, Long.class, resolvedAttachmentId);
        } catch (Exception ignored) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment does not exist");
        }
        if (!resourceId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "attachment does not belong to this resource");
        }
        return downloadAttachment(resolvedAttachmentId, accountId);
    }
}

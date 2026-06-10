package com.resourcesharing.forum.service.resource;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.point.PointRuleService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

@Service("designSpecResourceFileService")
public class FileService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final PointManager pointManager;
    private final PointRuleService pointRules;
    private final Path rootDir;

    public FileService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            PointManager pointManager,
            PointRuleService pointRules,
            @Value("${forum.upload.root-dir:./uploads}") String rootDir
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.pointManager = pointManager;
        this.pointRules = pointRules;
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> downloadAttachment(Long attachmentId, Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("recordId", 1L, "fileName", "demo.zip", "downloadable", true,
                    "downloadUrl", "/api/v1/attachments/" + attachmentId + "/stream");
        }
        return txSupport.required(() -> {
            Long memberId = lookup.requireMemberId(accountId);
            Map<String, Object> attachment = jdbc.queryForObject("""
                    SELECT fa.id, fa.original_file_name, fa.owner_id AS resource_id, fa.storage_path,
                           r.status, r.publisher_id
                    FROM file_attachment fa
                    JOIN resource_info r ON r.id = fa.owner_id AND fa.owner_type = 'RESOURCE'
                    WHERE fa.id = ? AND fa.status = 'NORMAL' AND fa.deleted_at IS NULL
                    """, (rs, rowNum) -> values.map(
                    "id", rs.getLong("id"),
                    "fileName", rs.getString("original_file_name"),
                    "resourceId", rs.getLong("resource_id"),
                    "storagePath", rs.getString("storage_path"),
                    "status", rs.getString("status"),
                    "publisherId", rs.getLong("publisher_id")
            ), attachmentId);
            if (!"PUBLISHED".equals(attachment.get("status"))) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Resource is not published");
            }
            Long resourceId = values.number(attachment.get("resourceId"), 0L);
            Long publisherId = values.number(attachment.get("publisherId"), 0L);
            ensureDailyDownloadLimit(jdbc, memberId);
            Path path = resolveStoragePath(String.valueOf(attachment.get("storagePath")));
            if (!path.startsWith(rootDir) || !Files.exists(path) || !Files.isRegularFile(path)) {
                return values.map(
                        "recordId", null,
                        "fileName", attachment.get("fileName"),
                        "downloadable", false,
                        "downloadUrl", null,
                        "message", "附件文件不存在，请联系管理员重新上传"
                );
            }
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
                if (!publisherId.equals(memberId)) {
                    pointManager.earn(publisherId, pointRules.resourceDownloadedPoints(), "RESOURCE_DOWNLOADED",
                            "RESOURCE", resourceId, memberId, "Resource downloaded reward",
                            "resource-download:" + resourceId + ":" + memberId);
                }
            }
            incrementDailyDownloadCount(jdbc, memberId);
            return values.map("recordId", values.key(keyHolder), "fileName", attachment.get("fileName"),
                    "downloadable", true, "downloadUrl", "/api/v1/attachments/" + attachmentId + "/stream");
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

    private Path resolveStoragePath(String storagePath) {
        String normalized = storagePath == null ? "" : storagePath.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        Path candidate = Paths.get(normalized);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        String rootName = rootDir.getFileName() == null ? "" : rootDir.getFileName().toString();
        if (!rootName.isBlank() && normalized.startsWith(rootName + "/")) {
            normalized = normalized.substring(rootName.length() + 1);
        }
        return rootDir.resolve(normalized).normalize();
    }

    private void ensureDailyDownloadLimit(JdbcTemplate jdbc, Long memberId) {
        Integer limit = jdbc.queryForObject("""
                SELECT COALESCE(ml.daily_download_limit, 0)
                FROM member_point_account mpa
                LEFT JOIN membership_level ml ON ml.id = mpa.level_id AND ml.deleted_at IS NULL
                WHERE mpa.member_id = ? AND mpa.deleted_at IS NULL
                """, Integer.class, memberId);
        if (limit == null || limit <= 0) {
            return;
        }
        Integer downloadedToday = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM download_record
                WHERE member_id = ?
                  AND status = 'SUCCESS'
                  AND deleted_at IS NULL
                  AND DATE(created_at) = CURRENT_DATE()
                """, Integer.class, memberId);
        if (downloadedToday != null && downloadedToday >= limit) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Daily download limit has been reached");
        }
    }

    private void incrementDailyDownloadCount(JdbcTemplate jdbc, Long memberId) {
        jdbc.update("""
                INSERT INTO member_daily_stat(stat_date, member_id, download_count)
                VALUES (CURRENT_DATE(), ?, 1)
                ON DUPLICATE KEY UPDATE download_count = download_count + 1
                """, memberId);
    }
}

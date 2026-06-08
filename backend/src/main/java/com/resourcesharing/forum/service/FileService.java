package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.dto.FileDtos.AttachmentView;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileService {
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "ps1", "msi", "com", "scr", "jar", "war", "php", "jsp", "asp", "aspx"
    );

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final Path rootDir;
    private final Set<String> allowedExtensions;
    private final long maxFileSize;

    public FileService(
            ObjectProvider<JdbcTemplate> jdbcProvider,
            @Value("${forum.upload.root-dir:./uploads}") String rootDir,
            @Value("${forum.upload.allowed-types:pdf,doc,docx,ppt,pptx,xls,xlsx,zip,rar,7z,png,jpg,jpeg,txt,md}") String allowedTypes,
            @Value("${forum.upload.max-file-size-mb:100}") long maxFileSizeMb
    ) {
        this.jdbcProvider = jdbcProvider;
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
        this.allowedExtensions = Arrays.stream(allowedTypes.split(","))
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .filter(type -> !type.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.maxFileSize = maxFileSizeMb * 1024L * 1024L;
    }

    @Transactional
    public AttachmentView upload(MultipartFile file, Long resourceId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件服务需要数据库连接");
        }
        requireNormalAccount(jdbc, accountId);
        validateFile(file);
        Long ownerId = resourceId == null ? 0L : resourceId;
        if (resourceId != null) {
            ensureResourceWritable(resourceId, accountId);
        }

        String originalName = sanitizeName(file.getOriginalFilename());
        String ext = extension(originalName);
        String storedName = UUID.randomUUID() + "." + ext;
        LocalDate today = LocalDate.now();
        Path relativePath = Path.of("resource", String.valueOf(ownerId), String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()), storedName);
        Path target = rootDir.resolve(relativePath).normalize();
        if (!target.startsWith(rootDir)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径不合法");
        }

        String hash = store(file, target);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO file_attachment(
                        owner_type, owner_id, uploader_id, original_file_name, stored_file_name,
                        file_ext, mime_type, file_size, file_hash, storage_path, status
                    ) VALUES ('RESOURCE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ownerId);
            statement.setLong(2, accountId);
            statement.setString(3, originalName);
            statement.setString(4, storedName);
            statement.setString(5, ext);
            statement.setString(6, safeMime(file.getContentType()));
            statement.setLong(7, file.getSize());
            statement.setString(8, hash);
            statement.setString(9, relativePath.toString().replace('\\', '/'));
            statement.setString(10, resourceId == null ? "TEMP" : "NORMAL");
            return statement;
        }, keyHolder);

        Long id = keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
        return new AttachmentView(id, resourceId, originalName, ext, file.getSize(), resourceId == null ? "TEMP" : "NORMAL");
    }

    public List<AttachmentView> listByResource(Long resourceId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT id, owner_id, original_file_name, file_ext, file_size, status
                FROM file_attachment
                WHERE owner_type = 'RESOURCE' AND owner_id = ? AND deleted_at IS NULL
                ORDER BY id DESC
                """, (rs, rowNum) -> new AttachmentView(
                rs.getLong("id"),
                rs.getLong("owner_id"),
                rs.getString("original_file_name"),
                rs.getString("file_ext"),
                rs.getLong("file_size"),
                rs.getString("status")
        ), resourceId);
    }

    public AttachmentStream stream(Long attachmentId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment does not exist");
        }
        requireNormalAccount(jdbc, accountId);
        AttachmentStream stream;
        try {
            stream = jdbc.queryForObject("""
                    SELECT fa.original_file_name, fa.mime_type, fa.file_size, fa.storage_path, r.status
                    FROM file_attachment fa
                    JOIN resource_info r ON r.id = fa.owner_id AND fa.owner_type = 'RESOURCE'
                    WHERE fa.id = ? AND fa.status = 'NORMAL' AND fa.deleted_at IS NULL
                    """, (rs, rowNum) -> new AttachmentStream(
                    rs.getString("original_file_name"),
                    safeMime(rs.getString("mime_type")),
                    rs.getLong("file_size"),
                    rootDir.resolve(rs.getString("storage_path")).normalize(),
                    rs.getString("status")
            ), attachmentId);
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment does not exist");
        }
        if (stream == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "attachment does not exist");
        }
        if (!"PUBLISHED".equals(stream.resourceStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "resource is not published");
        }
        if (!stream.path().startsWith(rootDir) || !Files.exists(stream.path()) || !Files.isRegularFile(stream.path())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "file does not exist");
        }
        return stream;
    }

    @Transactional
    public AttachmentView bindToResource(Long attachmentId, Long resourceId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件服务需要数据库连接");
        }
        requireNormalAccount(jdbc, accountId);
        ensureResourceWritable(resourceId, accountId);
        int updated = jdbc.update("""
                UPDATE file_attachment
                SET owner_id = ?, status = 'NORMAL', update_time = NOW(3)
                WHERE id = ? AND uploader_id = ? AND owner_type = 'RESOURCE' AND status = 'TEMP' AND deleted_at IS NULL
                """, resourceId, attachmentId, accountId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在或不可绑定");
        }
        return jdbc.queryForObject("""
                SELECT id, owner_id, original_file_name, file_ext, file_size, status
                FROM file_attachment
                WHERE id = ?
                """, (rs, rowNum) -> new AttachmentView(
                rs.getLong("id"),
                rs.getLong("owner_id"),
                rs.getString("original_file_name"),
                rs.getString("file_ext"),
                rs.getLong("file_size"),
                rs.getString("status")
        ), attachmentId);
    }

    private void requireNormalAccount(JdbcTemplate jdbc, Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后再上传文件");
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM user_account
                WHERE id = ? AND status = 'NORMAL' AND deleted_at IS NULL
                """, Integer.class, accountId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在或已被禁用");
        }
    }

    private void ensureResourceWritable(Long resourceId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件服务需要数据库连接");
        }
        Integer allowed = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM resource_info r
                JOIN member_profile mp ON mp.id = r.publisher_id
                JOIN user_account ua ON ua.id = mp.account_id
                WHERE r.id = ? AND r.deleted_at IS NULL AND r.status IN ('DRAFT', 'REJECTED', 'PENDING_REVIEW')
                  AND (ua.id = ? OR EXISTS (
                      SELECT 1 FROM user_account admin
                      WHERE admin.id = ? AND admin.role IN ('ADMIN', 'SUPER_ADMIN', 'AUDITOR') AND admin.status = 'NORMAL'
                  ))
                """, Integer.class, resourceId, accountId, accountId);
        if (allowed == null || allowed == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权绑定该资源附件");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件超过大小限制");
        }
        String name = sanitizeName(file.getOriginalFilename());
        String ext = extension(name);
        if (BLOCKED_EXTENSIONS.contains(ext) || !allowedExtensions.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件类型不允许");
        }
        String mime = safeMime(file.getContentType());
        if (mime.contains("x-msdownload") || mime.contains("x-sh") || mime.contains("php")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件 MIME 类型不允许");
        }
    }

    private String store(MultipartFile file, Path target) {
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(in, target);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    private static String sanitizeName(String name) {
        String safe = StringUtils.cleanPath(name == null ? "upload.bin" : name).replace('\\', '/');
        if (safe.contains("../") || safe.contains("/") || safe.contains("\0")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不合法");
        }
        return safe;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件缺少扩展名");
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeMime(String mime) {
        return mime == null ? "application/octet-stream" : mime.toLowerCase(Locale.ROOT);
    }

    private JdbcTemplate jdbc() {
        return jdbcProvider.getIfAvailable();
    }

    public record AttachmentStream(String fileName, String mimeType, long fileSize, Path path, String resourceStatus) {
    }
}

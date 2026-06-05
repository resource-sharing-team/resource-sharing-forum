package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.domain.statemachine.RequestStateMachine;
import com.resourcesharing.forum.domain.statemachine.ResourceStateMachine;
import com.resourcesharing.forum.security.JwtProperties;
import com.resourcesharing.forum.security.JwtService;
import com.resourcesharing.forum.service.point.PointManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class DesignSpecForumService {
    private static final long DEFAULT_ACCOUNT_ID = 1L;
    private static final long DEFAULT_MEMBER_ID = 1L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ObjectProvider<JdbcTemplate> jdbcProvider;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final PointManager pointManager;

    public DesignSpecForumService(
            ObjectProvider<JdbcTemplate> jdbcProvider,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider,
            JwtService jwtService,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService,
            PointManager pointManager
    ) {
        this.jdbcProvider = jdbcProvider;
        this.transactionManagerProvider = transactionManagerProvider;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.pointManager = pointManager;
    }

    public Map<String, Object> login(Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        String account = value(request, "account", value(request, "username", ""));
        String password = value(request, "password", "");
        if (jdbc == null) {
            return tokenResponse(defaultUser(DEFAULT_ACCOUNT_ID), "MEMBER");
        }
        try {
            Map<String, Object> row = jdbc.queryForObject("""
                    SELECT id, username, email, password_hash, role, status, failed_login_count, locked_until
                    FROM user_account
                    WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map(
                    "id", rs.getLong("id"),
                    "username", rs.getString("username"),
                    "email", rs.getString("email"),
                    "passwordHash", rs.getString("password_hash"),
                    "role", rs.getString("role"),
                    "status", rs.getString("status"),
                    "failedLoginCount", rs.getInt("failed_login_count"),
                    "lockedUntil", rs.getObject("locked_until", LocalDateTime.class)
            ), account, account);
            Long accountId = number(row.get("id"), DEFAULT_ACCOUNT_ID);
            if (!"NORMAL".equals(row.get("status"))) {
                recordLogin(jdbc, accountId, account, "FAILED", "ACCOUNT_DISABLED");
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号已被禁用");
            }
            LocalDateTime lockedUntil = (LocalDateTime) row.get("lockedUntil");
            if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
                recordLogin(jdbc, accountId, account, "FAILED", "ACCOUNT_LOCKED");
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号临时锁定");
            }
            String hash = String.valueOf(row.get("passwordHash"));
            if (!passwordEncoder.matches(password, hash)) {
                int nextFailCount = (int) number(row.get("failedLoginCount"), 0L).longValue() + 1;
                jdbc.update("""
                        UPDATE user_account
                        SET failed_login_count = ?,
                            locked_until = IF(? >= 5, DATE_ADD(NOW(3), INTERVAL 10 MINUTE), locked_until)
                        WHERE id = ?
                        """, nextFailCount, nextFailCount, accountId);
                recordLogin(jdbc, accountId, account, "FAILED", "PASSWORD_ERROR");
                throw new BusinessException(ErrorCode.UNAUTHORIZED, nextFailCount >= 5 ? "账号临时锁定" : "账号或密码错误");
            }
            jdbc.update("""
                    UPDATE user_account
                    SET failed_login_count = 0, locked_until = NULL, last_login_time = NOW(3)
                    WHERE id = ?
                    """, accountId);
            recordLogin(jdbc, accountId, account, "SUCCESS", null);
            return tokenResponse(userProfile(accountId), roleForToken(String.valueOf(row.get("role"))));
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
    }

    public Map<String, Object> register(Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        String username = value(request, "username", "");
        String email = value(request, "email", "");
        String password = value(request, "password", "");
        if (jdbc == null) {
            return tokenResponse(defaultUser(DEFAULT_ACCOUNT_ID), "MEMBER");
        }
        return inTransaction(() -> {
            KeyHolder accountKey = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO user_account(username, email, password_hash, role, status, password_changed_time)
                        VALUES (?, ?, ?, 'USER', 'NORMAL', NOW(3))
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, username);
                statement.setString(2, email);
                statement.setString(3, passwordEncoder.encode(password));
                return statement;
            }, accountKey);
            Long accountId = key(accountKey);
            KeyHolder memberKey = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO member_profile(account_id, nickname, gender, bio)
                        VALUES (?, ?, 'UNKNOWN', '')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, accountId);
                statement.setString(2, username);
                return statement;
            }, memberKey);
            Long memberId = key(memberKey);
            jdbc.update("""
                    INSERT INTO member_point_account(member_id, level_id, current_points, frozen_points, total_earned_points, total_spent_points)
                    VALUES (?, 1, 0, 0, 0, 0)
                    """, memberId);
            jdbc.update("""
                    INSERT INTO point_flow(member_id, flow_type, scene, points_change, frozen_change,
                                           before_points, after_points, before_frozen_points, after_frozen_points,
                                           related_type, related_id, operator_id, description)
                    VALUES (?, 'EARN', 'REGISTER', 0, 0, 0, 0, 0, 0, 'USER_ACCOUNT', ?, ?, '注册初始化积分账户')
                    """, memberId, accountId, accountId);
            return tokenResponse(userProfile(accountId), "MEMBER");
        });
    }

    public Map<String, Object> userProfile(Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null || accountId == null) {
            return defaultUser(accountId);
        }
        try {
            return jdbc.queryForObject("""
                    SELECT ua.id, ua.username, ua.email, ua.role, ua.password_changed_time,
                           mp.id AS member_id, mp.nickname, mp.avatar_url, mp.bio,
                           ml.level_name, mpa.current_points, mpa.frozen_points
                    FROM user_account ua
                    LEFT JOIN member_profile mp ON mp.account_id = ua.id AND mp.deleted_at IS NULL
                    LEFT JOIN member_point_account mpa ON mpa.member_id = mp.id AND mpa.deleted_at IS NULL
                    LEFT JOIN membership_level ml ON ml.id = mpa.level_id
                    WHERE ua.id = ?
                    """, userMapper(), accountId);
        } catch (DataAccessException ignored) {
            return defaultUser(accountId);
        }
    }

    public Map<String, Object> updateUserProfile(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return defaultUser(accountId);
        }
        jdbc.update("""
                UPDATE member_profile
                SET nickname = COALESCE(?, nickname),
                    bio = COALESCE(?, bio),
                    avatar_url = COALESCE(?, avatar_url)
                WHERE account_id = ?
                """, nullable(request, "nickname"), nullable(request, "bio"), nullable(request, "avatar"), accountId);
        return userProfile(accountId);
    }

    public Map<String, Object> changePassword(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("ok", true);
        }
        String oldPassword = firstNonBlank(value(request, "oldPassword", ""), value(request, "currentPassword", ""));
        String newPassword = firstNonBlank(value(request, "newPassword", ""), value(request, "password", ""));
        if (oldPassword.isBlank() || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "旧密码不能为空，新密码长度至少 6 位");
        }
        Map<String, Object> account = jdbc.queryForObject("""
                SELECT password_hash FROM user_account
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> map("passwordHash", rs.getString("password_hash")), accountId);
        if (account == null || !passwordEncoder.matches(oldPassword, String.valueOf(account.get("passwordHash")))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "旧密码不正确");
        }
        if (passwordEncoder.matches(newPassword, String.valueOf(account.get("passwordHash")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与旧密码相同");
        }
        jdbc.update("""
                UPDATE user_account
                SET password_hash = ?, password_changed_time = NOW(3)
                WHERE id = ?
                """, passwordEncoder.encode(newPassword), accountId);
        return map("ok", true, "passwordUpdatedAt", today());
    }

    public Map<String, Object> changeEmail(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            Map<String, Object> user = defaultUser(accountId);
            user.put("email", firstNonBlank(value(request, "newEmail", ""), value(request, "email", ""), String.valueOf(user.get("email"))));
            return user;
        }
        String oldEmail = value(request, "oldEmail", "");
        String newEmail = firstNonBlank(value(request, "newEmail", ""), value(request, "email", ""));
        if (newEmail.isBlank() || !newEmail.contains("@")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式不正确");
        }
        Map<String, Object> account = jdbc.queryForObject("""
                SELECT email FROM user_account
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> map("email", rs.getString("email")), accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        String currentEmail = String.valueOf(account.get("email"));
        if (!oldEmail.isBlank() && !Objects.equals(oldEmail, currentEmail)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原邮箱不匹配");
        }
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_account
                WHERE email = ? AND id <> ? AND deleted_at IS NULL
                """, Integer.class, newEmail, accountId);
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱已被占用");
        }
        jdbc.update("""
                UPDATE user_account
                SET email = ?
                WHERE id = ?
                """, newEmail, accountId);
        return userProfile(accountId);
    }

    public Map<String, Object> requestResetPasswordCode(Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        String account = firstNonBlank(value(request, "account", ""), value(request, "email", ""));
        if (jdbc == null) {
            return map("ok", true, "devCode", "000000");
        }
        Map<String, Object> row = jdbc.queryForObject("""
                SELECT id, email
                FROM user_account
                WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                """, (rs, rowNum) -> map("id", rs.getLong("id"), "email", rs.getString("email")), account, account);
        String code = "000000";
        jdbc.update("""
                INSERT INTO email_verification_code(account_id, email, scene, code_hash, status, expire_time)
                VALUES (?, ?, 'RESET_PASSWORD', ?, 'UNUSED', DATE_ADD(NOW(3), INTERVAL 10 MINUTE))
                """, row.get("id"), row.get("email"), passwordEncoder.encode(code));
        return map("ok", true, "email", row.get("email"), "devCode", code, "expiresInMinutes", 10);
    }

    public Map<String, Object> resetPassword(Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        String account = firstNonBlank(value(request, "account", ""), value(request, "email", ""));
        String code = value(request, "code", "");
        String password = firstNonBlank(value(request, "newPassword", ""), value(request, "password", ""));
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码长度至少 6 位");
        }
        if (jdbc == null) {
            return map("ok", true);
        }
        return inTransaction(() -> {
            Map<String, Object> row = jdbc.queryForObject("""
                    SELECT id, email
                    FROM user_account
                    WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map("id", rs.getLong("id"), "email", rs.getString("email")), account, account);
            List<Map<String, Object>> codes = jdbc.query("""
                    SELECT id, code_hash
                    FROM email_verification_code
                    WHERE account_id = ? AND scene = 'RESET_PASSWORD' AND status = 'UNUSED' AND expire_time > NOW(3)
                    ORDER BY id DESC
                    LIMIT 1
                    """, (rs, rowNum) -> map("id", rs.getLong("id"), "hash", rs.getString("code_hash")), row.get("id"));
            if (codes.isEmpty() || !passwordEncoder.matches(code, String.valueOf(codes.get(0).get("hash")))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误或已过期");
            }
            jdbc.update("UPDATE email_verification_code SET status = 'USED', used_time = NOW(3) WHERE id = ?", codes.get(0).get("id"));
            jdbc.update("""
                    UPDATE user_account
                    SET password_hash = ?, password_changed_time = NOW(3), failed_login_count = 0, locked_until = NULL, status = IF(status = 'LOCKED', 'NORMAL', status)
                    WHERE id = ?
                    """, passwordEncoder.encode(password), row.get("id"));
            return map("ok", true, "email", row.get("email"));
        });
    }

    public PageResult<Map<String, Object>> listResources(Map<String, String> params, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        int page = page(params);
        int size = size(params);
        if (jdbc == null) {
            return new PageResult<>(1, List.of(defaultResource()), page, size);
        }
        try {
            String keyword = blankToNull(params.get("keyword"));
            String category = firstNonBlank(params.get("categoryId"), params.get("category2"), params.get("cate2"));
            String type = blankToNull(params.get("resourceType"));
            if (type == null) {
                type = blankToNull(params.get("type"));
            }
            StringBuilder where = new StringBuilder("WHERE r.deleted_at IS NULL AND r.status = 'PUBLISHED'");
            List<Object> args = new ArrayList<>();
            if (keyword != null) {
                where.append(" AND (r.title LIKE ? OR r.summary LIKE ? OR r.description LIKE ?)");
                String like = "%" + keyword + "%";
                args.add(like);
                args.add(like);
                args.add(like);
            }
            if (!category.isBlank()) {
                where.append(" AND r.category_id = ?");
                args.add(longValue(category, 0L));
            }
            if (type != null) {
                where.append(" AND r.resource_type = ?");
                args.add(resourceType(type));
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
                    ORDER BY r.published_time DESC, r.id DESC
                    LIMIT ?, ?
                    """.formatted(where), resourceMapper(accountId), args.toArray());
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(1, List.of(defaultResource()), page, size);
        }
    }

    public Map<String, Object> publishResource(Long accountId, Map<String, Object> request, List<MultipartFile> files) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            Map<String, Object> resource = defaultResource();
            resource.put("status", "PENDING_REVIEW");
            return resource;
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Long categoryId = number(firstPresent(request, "categoryId", "category2"), 11L);
            String title = firstNonBlank(value(request, "title", ""), "新资源发布");
            String summary = firstNonBlank(value(request, "summary", ""), value(request, "description", ""), "资源简介");
            String detail = firstNonBlank(value(request, "detail", ""), value(request, "description", ""), "资源详情");
            String type = resourceType(firstNonBlank(value(request, "resourceType", ""), value(request, "type", "DOCUMENT")));
            boolean draft = Boolean.parseBoolean(value(request, "draft", "false")) || "DRAFT".equalsIgnoreCase(value(request, "status", ""));
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
                statement.setString(7, blankToNull(value(request, "externalUrl", "")));
                statement.setString(8, initialStatus);
                statement.setString(9, initialStatus);
                return statement;
            }, resourceKey);
            Long resourceId = key(resourceKey);
            insertResourceTags(jdbc, resourceId, value(request, "tags", ""));
            insertAttachments(jdbc, "RESOURCE", resourceId, accountId, files, firstNonBlank(value(request, "fileName", ""), "uploaded-file.zip"));
            jdbc.update("""
                    INSERT INTO resource_version(resource_id, version_no, title, summary, description, category_id, resource_type, submitter_id)
                    VALUES (?, 1, ?, ?, ?, ?, ?, ?)
                    """, resourceId, title, summary, detail, categoryId, type, memberId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, NULL, ?, ?, ?)
                    """, resourceId, initialStatus, accountId, draft ? "保存资源草稿" : "用户提交资源审核");
            return resource(resourceId, accountId);
        });
    }

    public Map<String, Object> resourceDetail(Long resourceId, Long accountId) {
        return map(
                "resource", resource(resourceId, accountId),
                "comments", comments("RESOURCE", resourceId, accountId, 1, 20).list()
        );
    }

    public void deleteResource(Long resourceId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        inTransaction(() -> {
            if (accountId == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后再删除资源");
            }
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT r.status, r.publisher_id, ua.role
                    FROM resource_info r
                    JOIN user_account ua ON ua.id = ?
                    WHERE r.id = ? AND r.deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map(
                    "status", rs.getString("status"),
                    "publisherId", rs.getLong("publisher_id"),
                    "role", rs.getString("role")
            ), accountId, resourceId);
            boolean admin = List.of("ADMIN", "SUPER_ADMIN", "AUDITOR").contains(String.valueOf(before.get("role")));
            Long memberId = admin ? null : requireMemberId(accountId);
            if (!admin && !Objects.equals(number(before.get("publisherId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该资源");
            }
            jdbc.update("UPDATE resource_info SET status = 'DELETED', deleted_at = NOW(3) WHERE id = ?", resourceId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'DELETED', ?, ?)
                    """, resourceId, before.get("status"), accountId, admin ? "管理员删除资源" : "发布者删除资源");
            if (admin) {
                adminLog(adminProfileId(accountId), "RESOURCE_DELETED", "RESOURCE", resourceId, String.valueOf(before.get("status")), "DELETED");
            }
            return null;
        });
    }

    public Map<String, Object> auditResource(Long resourceId, Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", resourceId, "status", "PUBLISHED", "auditResult", "APPROVED");
        }
        String action = value(request, "action", value(request, "auditResult", "APPROVE")).toUpperCase();
        if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
            return transitionResourceByAdmin(resourceId, adminAccountId, "APPROVE", request);
        }
        if ("REJECT".equals(action) || "REJECTED".equals(action)) {
            return transitionResourceByAdmin(resourceId, adminAccountId, "REJECT", request);
        }
        return inTransaction(() -> {
            String auditAction = value(request, "action", value(request, "auditResult", "APPROVE")).toUpperCase();
            boolean approved = "APPROVE".equals(auditAction) || "APPROVED".equals(auditAction);
            String after = approved ? "PUBLISHED" : "REJECTED";
            String auditResult = approved ? "APPROVED" : "REJECTED";
            String reason = firstNonBlank(value(request, "reason", ""), approved ? "审核通过" : "审核驳回");
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT status, current_version_no FROM resource_info WHERE id = ? FOR UPDATE
                    """, (rs, rowNum) -> map("status", rs.getString("status"), "version", rs.getInt("current_version_no")), resourceId);
            jdbc.update("""
                    UPDATE resource_info
                    SET status = ?, published_time = IF(? = 'PUBLISHED', NOW(3), published_time), reject_reason = IF(? = 'REJECTED', ?, NULL)
                    WHERE id = ?
                    """, after, after, after, reason, resourceId);
            Long adminProfileId = adminProfileId(adminAccountId);
            jdbc.update("""
                    INSERT INTO resource_audit_record(resource_id, version_no, auditor_id, audit_result, reason)
                    VALUES (?, ?, ?, ?, ?)
                    """, resourceId, number(before.get("version"), 1L), adminProfileId, auditResult, reason);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, ?, ?, ?)
                    """, resourceId, before.get("status"), after, adminAccountId, reason);
            adminLog(adminProfileId, "RESOURCE_AUDIT", "RESOURCE", resourceId, String.valueOf(before.get("status")), after);
            return resource(resourceId, adminAccountId);
        });
    }

    public PageResult<Map<String, Object>> adminListResources(Map<String, String> params, Long adminAccountId) {
        JdbcTemplate jdbc = jdbc();
        int page = page(params);
        int size = size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String status = firstNonBlank(params.get("status"), params.get("resourceStatus"));
        String keyword = blankToNull(params.get("keyword"));
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
                """.formatted(where), resourceMapper(adminAccountId), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> transitionResourceByAdmin(Long resourceId, Long adminAccountId, String action, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", resourceId, "status", "PUBLISHED");
        }
        return inTransaction(() -> {
            String normalized = firstNonBlank(action, value(request, "action", "")).toUpperCase();
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT id, publisher_id, status, current_version_no, title
                    FROM resource_info
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map(
                    "id", rs.getLong("id"),
                    "publisherId", rs.getLong("publisher_id"),
                    "status", rs.getString("status"),
                    "version", rs.getInt("current_version_no"),
                    "title", rs.getString("title")
            ), resourceId);
            String from = String.valueOf(before.get("status"));
            String to = ResourceStateMachine.targetStatusForAction(normalized);
            String auditResult = auditResultForAction(normalized, to);
            String reason = firstNonBlank(value(request, "reason", ""), defaultResourceReason(normalized));
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
            Long adminProfileId = adminProfileId(adminAccountId);
            jdbc.update("""
                    INSERT INTO resource_audit_record(resource_id, version_no, auditor_id, audit_result, reason)
                    VALUES (?, ?, ?, ?, ?)
                    """, resourceId, number(before.get("version"), 1L), adminProfileId, auditResult, reason);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, ?, ?, ?)
                    """, resourceId, from, to, adminAccountId, reason);
            adminLog(adminProfileId, "RESOURCE_" + auditResult, "RESOURCE", resourceId, from, to);
            notifyMember(number(before.get("publisherId"), 0L), "RESOURCE_STATUS", "资源状态更新",
                    "资源《" + before.get("title") + "》状态已变更为 " + to + "，原因：" + reason, "RESOURCE", resourceId);
            return resource(resourceId, adminAccountId);
        });
    }

    public Map<String, Object> submitResource(Long resourceId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return resource(resourceId, accountId);
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT status FROM resource_info
                    WHERE id = ? AND publisher_id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map("status", rs.getString("status")), resourceId, memberId);
            String from = String.valueOf(before.get("status"));
            ResourceStateMachine.assertCanTransit(from, "PENDING_REVIEW", "SUBMIT", "MEMBER", true, "发布者提交审核");
            jdbc.update("UPDATE resource_info SET status = 'PENDING_REVIEW', submitted_time = NOW(3) WHERE id = ?", resourceId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'PENDING_REVIEW', ?, '发布者提交审核')
                    """, resourceId, from, accountId);
            return resource(resourceId, accountId);
        });
    }

    public Map<String, Object> withdrawResource(Long resourceId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return resource(resourceId, accountId);
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Map<String, Object> before = jdbc.queryForObject("""
                    SELECT status FROM resource_info
                    WHERE id = ? AND publisher_id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map("status", rs.getString("status")), resourceId, memberId);
            String reason = firstNonBlank(value(request, "reason", ""), "发布者撤回修改");
            ResourceStateMachine.assertCanTransit(String.valueOf(before.get("status")), "DRAFT", "WITHDRAW", "MEMBER", true, reason);
            jdbc.update("UPDATE resource_info SET status = 'DRAFT' WHERE id = ?", resourceId);
            jdbc.update("""
                    INSERT INTO resource_status_log(resource_id, from_status, to_status, operator_id, reason)
                    VALUES (?, 'PENDING_REVIEW', 'DRAFT', ?, ?)
                    """, resourceId, accountId, reason);
            return resource(resourceId, accountId);
        });
    }

    public Map<String, Object> toggleResourceInteraction(Long resourceId, String action, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return defaultResource();
        }
        Long memberId = requireMemberId(accountId);
        String actionType = "favorite".equalsIgnoreCase(action) ? "FAVORITE" : "LIKE";
        String column = "FAVORITE".equals(actionType) ? "favorite_count" : "like_count";
        jdbc.update("""
                INSERT INTO user_interaction(member_id, target_type, target_id, action_type, status)
                VALUES (?, 'RESOURCE', ?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE status = IF(status = 'ACTIVE', 'CANCELLED', 'ACTIVE'), update_time = NOW(3)
                """, memberId, resourceId, actionType);
        jdbc.update("""
                UPDATE resource_info r
                SET %s = (
                    SELECT COUNT(*) FROM user_interaction ui
                    WHERE ui.target_type = 'RESOURCE' AND ui.action_type = ? AND ui.target_id = r.id AND ui.status = 'ACTIVE'
                )
                WHERE r.id = ?
                """.formatted(column), actionType, resourceId);
        return resource(resourceId, accountId);
    }

    public Map<String, Object> rateResource(Long resourceId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        int score = Math.max(1, Math.min(5, (int) number(firstPresent(request, "score"), 5L).longValue()));
        if (jdbc == null) {
            Map<String, Object> resource = defaultResource();
            resource.put("userRating", score);
            return resource;
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Integer downloaded = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM download_record
                    WHERE member_id = ? AND resource_id = ? AND status = 'SUCCESS'
                    """, Integer.class, memberId, resourceId);
            if (downloaded == null || downloaded == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "用户成功下载资源后才能评分");
            }
            jdbc.update("""
                    INSERT INTO resource_rating(member_id, resource_id, score)
                    VALUES (?, ?, ?)
                    """, memberId, resourceId, score);
            jdbc.update("""
                    UPDATE resource_info r
                    SET rating_count = (SELECT COUNT(*) FROM resource_rating rr WHERE rr.resource_id = r.id),
                        average_rating = COALESCE((SELECT ROUND(AVG(rr.score), 2) FROM resource_rating rr WHERE rr.resource_id = r.id), 0)
                    WHERE r.id = ?
                    """, resourceId);
            return resource(resourceId, accountId);
        });
    }

    public Map<String, Object> downloadAttachment(Long attachmentId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("recordId", 1L, "fileName", "demo.zip", "downloadUrl", "/api/v1/attachments/" + attachmentId + "/download");
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Map<String, Object> attachment = jdbc.queryForObject("""
                    SELECT fa.id, fa.original_file_name, fa.owner_id AS resource_id, r.status
                    FROM file_attachment fa
                    JOIN resource_info r ON r.id = fa.owner_id AND fa.owner_type = 'RESOURCE'
                    WHERE fa.id = ? AND fa.status = 'NORMAL' AND fa.deleted_at IS NULL
                    """, (rs, rowNum) -> map(
                    "id", rs.getLong("id"),
                    "fileName", rs.getString("original_file_name"),
                    "resourceId", rs.getLong("resource_id"),
                    "status", rs.getString("status")
            ), attachmentId);
            if (!"PUBLISHED".equals(attachment.get("status"))) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "资源未发布或已下架");
            }
            Long resourceId = number(attachment.get("resourceId"), 0L);
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
            return map("recordId", key(keyHolder), "fileName", attachment.get("fileName"), "downloadUrl", "/api/v1/attachments/" + attachmentId + "/download");
        });
    }

    public PageResult<Map<String, Object>> listRequests(Map<String, String> params) {
        JdbcTemplate jdbc = jdbc();
        int page = page(params);
        int size = size(params);
        if (jdbc == null) {
            return new PageResult<>(1, List.of(defaultRequest()), page, size);
        }
        try {
            String keyword = blankToNull(params.get("keyword"));
            StringBuilder where = new StringBuilder("WHERE rp.deleted_at IS NULL");
            List<Object> args = new ArrayList<>();
            if (keyword != null) {
                where.append(" AND (rp.title LIKE ? OR rp.content LIKE ?)");
                String like = "%" + keyword + "%";
                args.add(like);
                args.add(like);
            }
            long total = jdbc.queryForObject("SELECT COUNT(*) FROM request_post rp " + where, Long.class, args.toArray());
            args.add((page - 1) * size);
            args.add(size);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT rp.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM request_post rp
                    JOIN member_profile mp ON mp.id = rp.requester_id
                    LEFT JOIN resource_category c2 ON c2.id = rp.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    %s
                    ORDER BY rp.create_time DESC, rp.id DESC
                    LIMIT ?, ?
                    """.formatted(where), requestMapper(), args.toArray());
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(1, List.of(defaultRequest()), page, size);
        }
    }

    public Map<String, Object> createRequest(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return defaultRequest();
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            int reward = Math.max(0, (int) number(firstPresent(request, "rewardPoints", "points"), 0L).longValue());
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO request_post(requester_id, category_id, title, content, expected_format, reward_points, status, deadline_time)
                        VALUES (?, ?, ?, ?, ?, ?, 'ONGOING', ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, memberId);
                Long categoryId = number(firstPresent(request, "categoryId", "category2"), 11L);
                if (categoryId == 0) {
                    statement.setObject(2, null);
                } else {
                    statement.setLong(2, categoryId);
                }
                statement.setString(3, firstNonBlank(value(request, "title", ""), "求一份课程项目资源说明文档"));
                statement.setString(4, firstNonBlank(value(request, "content", ""), value(request, "description", ""), "需要一份完整的课程项目资源说明文档，包含后端接口、数据库和运行步骤。"));
                statement.setString(5, firstNonBlank(value(request, "expectedFormat", ""), value(request, "format", ""), "不限"));
                statement.setInt(6, reward);
                statement.setObject(7, null);
                return statement;
            }, keyHolder);
            Long requestId = key(keyHolder);
            if (reward > 0) {
                pointManager.freezeForRequest(memberId, reward, requestId);
            }
            insertRequestTags(jdbc, requestId, value(request, "tags", ""));
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, NULL, 'ONGOING', ?, '发布悬赏求资源')
                    """, requestId, accountId);
            return requestPost(requestId);
        });
    }

    public Map<String, Object> requestDetail(Long requestId, Long accountId) {
        return map(
                "request", requestPost(requestId),
                "replies", listReplies(requestId, Map.of("page", "1", "size", "20")).list(),
                "comments", comments("REQUEST_POST", requestId, accountId, 1, 20).list()
        );
    }

    public void cancelRequest(Long requestId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Map<String, Object> row = jdbc.queryForObject("SELECT requester_id, reward_points, status FROM request_post WHERE id = ? FOR UPDATE",
                    (rs, rowNum) -> map("requesterId", rs.getLong("requester_id"), "reward", rs.getInt("reward_points"), "status", rs.getString("status")), requestId);
            if (!Objects.equals(number(row.get("requesterId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能取消自己发布的悬赏");
            }
            String reason = firstNonBlank(value(request, "reason", ""), "用户取消悬赏");
            RequestStateMachine.assertCanTransit(String.valueOf(row.get("status")), "CANCELLED", "CANCEL", "MEMBER", true, reason);
            int reward = (int) number(row.get("reward"), 0L).longValue();
            if (reward > 0) {
                pointManager.refundRequest(requestId);
            }
            jdbc.update("UPDATE request_post SET status = 'CANCELLED', closed_time = NOW(3) WHERE id = ?", requestId);
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, 'ONGOING', 'CANCELLED', ?, ?)
                    """, requestId, accountId, reason);
            return null;
        });
    }

    public PageResult<Map<String, Object>> listReplies(Long requestId, Map<String, String> params) {
        JdbcTemplate jdbc = jdbc();
        int page = page(params);
        int size = size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            long total = jdbc.queryForObject("SELECT COUNT(*) FROM request_reply WHERE request_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL", Long.class, requestId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT rr.*, mp.nickname AS author_name
                    FROM request_reply rr
                    JOIN member_profile mp ON mp.id = rr.replier_id
                    WHERE rr.request_id = ? AND rr.status = 'ACTIVE' AND rr.deleted_at IS NULL
                    ORDER BY rr.is_accepted DESC, rr.create_time DESC
                    LIMIT ?, ?
                    """, replyMapper(), requestId, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    public Map<String, Object> replyRequest(Long requestId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", 1L, "requestId", requestId, "content", value(request, "content", ""), "accepted", false);
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Map<String, Object> post = jdbc.queryForObject("SELECT requester_id, status FROM request_post WHERE id = ? FOR UPDATE",
                    (rs, rowNum) -> map("requesterId", rs.getLong("requester_id"), "status", rs.getString("status")), requestId);
            if ("ONGOING".equals(post.get("status")) && Objects.equals(number(post.get("requesterId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能回答自己的求资源帖");
            }
            if (!"ONGOING".equals(post.get("status"))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "悬赏不是进行中状态");
            }
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO request_reply(request_id, replier_id, content, resource_id, external_url, status)
                        VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, requestId);
                statement.setLong(2, memberId);
                statement.setString(3, firstNonBlank(value(request, "content", ""), "我可以提供相关资源。"));
                Long resourceId = number(firstPresent(request, "resourceId", "referencedResourceId"), 0L);
                if (resourceId == 0) {
                    statement.setObject(4, null);
                } else {
                    statement.setLong(4, resourceId);
                }
                statement.setString(5, blankToNull(value(request, "externalUrl", "")));
                return statement;
            }, keyHolder);
            jdbc.update("UPDATE request_post SET answer_count = answer_count + 1 WHERE id = ?", requestId);
            notifyMember(number(post.get("requesterId"), 0L), "REQUEST_REPLY", "求资源收到新回答", "你的求资源帖子收到一条新回答", "REQUEST_POST", requestId);
            return reply(key(keyHolder));
        });
    }

    public Map<String, Object> settleRequest(Long requestId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", requestId, "status", "RESOLVED");
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            Long replyId = number(firstPresent(request, "replyId", "answerId"), 0L);
            Map<String, Object> post = jdbc.queryForObject("SELECT requester_id, reward_points, status, accepted_reply_id FROM request_post WHERE id = ? FOR UPDATE",
                    (rs, rowNum) -> map("requesterId", rs.getLong("requester_id"), "reward", rs.getInt("reward_points"), "status", rs.getString("status"), "acceptedReplyId", rs.getObject("accepted_reply_id")), requestId);
            if (!Objects.equals(number(post.get("requesterId"), 0L), memberId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只有悬赏发布者可以采纳回答");
            }
            RequestStateMachine.assertCanTransit(String.valueOf(post.get("status")), "RESOLVED", "ACCEPT_REPLY", "MEMBER", true, "采纳回答并结算悬赏");
            if (post.get("acceptedReplyId") != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "悬赏不可结算");
            }
            Map<String, Object> reply = jdbc.queryForObject("SELECT replier_id FROM request_reply WHERE id = ? AND request_id = ? AND status = 'ACTIVE' FOR UPDATE",
                    (rs, rowNum) -> map("replierId", rs.getLong("replier_id")), replyId, requestId);
            int reward = (int) number(post.get("reward"), 0L).longValue();
            if (reward > 0) {
                pointManager.transferReward(requestId, number(reply.get("replierId"), 0L));
            }
            jdbc.update("UPDATE request_reply SET is_accepted = 1, accepted_time = NOW(3) WHERE id = ?", replyId);
            jdbc.update("UPDATE request_post SET status = 'RESOLVED', accepted_reply_id = ?, resolved_time = NOW(3) WHERE id = ?", replyId, requestId);
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, 'ONGOING', 'RESOLVED', ?, '采纳回答并结算悬赏')
                    """, requestId, accountId);
            notifyMember(number(reply.get("replierId"), 0L), "REQUEST_ACCEPTED", "你的回答已被采纳", "求资源回答已被采纳，悬赏积分已结算", "REQUEST_POST", requestId);
            return requestPost(requestId);
        });
    }

    public PageResult<Map<String, Object>> listComments(Map<String, String> params, Long accountId) {
        String targetType = firstNonBlank(params.get("targetType"), "RESOURCE");
        Long targetId = longValue(firstNonBlank(params.get("targetId"), "1"), 1L);
        return comments(targetType, targetId, accountId, page(params), size(params));
    }

    public Map<String, Object> addComment(Long accountId, Map<String, Object> request) {
        return addComment(
                firstNonBlank(value(request, "targetType", ""), "RESOURCE"),
                number(firstPresent(request, "targetId"), 1L),
                value(request, "content", ""),
                accountId,
                number(firstPresent(request, "parentId"), 0L),
                number(firstPresent(request, "toMemberId"), 0L)
        );
    }

    public Map<String, Object> addComment(String targetType, Long targetId, String content, Long accountId) {
        return addComment(targetType, targetId, content, accountId, 0L, 0L);
    }

    public Map<String, Object> commentDetail(Long commentId, Long accountId) {
        return comment(commentId, accountId);
    }

    public Map<String, Object> updateComment(Long commentId, Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", commentId, "content", value(request, "content", ""), "mine", true);
        }
        jdbc.update("""
                UPDATE comment_info
                SET content = ?
                WHERE id = ? AND member_id = ? AND status = 'ACTIVE'
                """, value(request, "content", ""), commentId, requireMemberId(accountId));
        return comment(commentId, accountId);
    }

    public void deleteComment(Long commentId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                UPDATE comment_info
                SET status = 'DELETED', deleted_at = NOW(3)
                WHERE id = ? AND member_id = ? AND status = 'ACTIVE'
                """, commentId, requireMemberId(accountId));
    }

    public Map<String, Object> likeComment(Long commentId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", commentId, "liked", true);
        }
        Long memberId = requireMemberId(accountId);
        jdbc.update("""
                INSERT INTO user_interaction(member_id, target_type, target_id, action_type, status)
                VALUES (?, 'COMMENT', ?, 'LIKE', 'ACTIVE')
                ON DUPLICATE KEY UPDATE status = IF(status = 'ACTIVE', 'CANCELLED', 'ACTIVE'), update_time = NOW(3)
                """, memberId, commentId);
        return comment(commentId, accountId);
    }

    public Map<String, Object> report(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("ok", true, "status", "PENDING");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO report_complaint(reporter_id, report_type, target_type, target_id, title, reason, proof_summary, contact_email, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, requireMemberId(accountId));
            String targetType = reportTarget(value(request, "targetType", value(request, "target", "RESOURCE")));
            statement.setString(2, reportType(value(request, "type", "RESOURCE"), targetType));
            statement.setString(3, targetType);
            statement.setLong(4, number(firstPresent(request, "targetId"), 0L));
            statement.setString(5, blankToNull(value(request, "title", "")));
            statement.setString(6, firstNonBlank(value(request, "reason", ""), "用户提交举报"));
            statement.setString(7, blankToNull(value(request, "proofSummary", "")));
            statement.setString(8, blankToNull(value(request, "contactEmail", "")));
            return statement;
        }, keyHolder);
        return map("ok", true, "id", key(keyHolder), "status", "PENDING");
    }

    public Map<String, Object> appeal(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("ok", true, "status", "PENDING");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO appeal_record(appellant_id, target_type, target_id, related_report_id, reason, status)
                    VALUES (?, ?, ?, ?, ?, 'PENDING')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, requireMemberId(accountId));
            statement.setString(2, firstNonBlank(value(request, "targetType", ""), "RESOURCE"));
            statement.setLong(3, number(firstPresent(request, "targetId"), 0L));
            Long reportId = number(firstPresent(request, "relatedReportId"), 0L);
            if (reportId == 0) {
                statement.setObject(4, null);
            } else {
                statement.setLong(4, reportId);
            }
            statement.setString(5, firstNonBlank(value(request, "reason", ""), "用户提交申诉"));
            return statement;
        }, keyHolder);
        return map("ok", true, "id", key(keyHolder), "status", "PENDING");
    }

    public Map<String, Object> handleReport(Long reportId, Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", reportId, "status", "RESOLVED");
        }
        Long adminId = adminProfileId(adminAccountId);
        String status = firstNonBlank(value(request, "status", ""), value(request, "result", ""), "RESOLVED").toUpperCase();
        if (!List.of("RESOLVED", "REJECTED", "PROCESSING").contains(status)) {
            status = "RESOLVED";
        }
        jdbc.update("""
                UPDATE report_complaint
                SET status = ?, handler_id = ?, handle_result = ?, handle_time = NOW(3)
                WHERE id = ?
                """, status, adminId, firstNonBlank(value(request, "handleResult", ""), value(request, "reason", ""), "管理员处理"), reportId);
        adminLog(adminId, "REPORT_HANDLE", "REPORT_COMPLAINT", reportId, "PENDING", status);
        return map("id", reportId, "status", status);
    }

    public Map<String, Object> handleAppeal(Long appealId, Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", appealId, "status", "APPROVED");
        }
        Long adminId = adminProfileId(adminAccountId);
        String status = firstNonBlank(value(request, "status", ""), value(request, "result", ""), "APPROVED").toUpperCase();
        if (!List.of("APPROVED", "REJECTED", "PROCESSING").contains(status)) {
            status = "APPROVED";
        }
        jdbc.update("""
                UPDATE appeal_record
                SET status = ?, handler_id = ?, handle_result = ?, handle_time = NOW(3)
                WHERE id = ?
                """, status, adminId, firstNonBlank(value(request, "handleResult", ""), value(request, "reason", ""), "管理员处理"), appealId);
        adminLog(adminId, "APPEAL_HANDLE", "APPEAL", appealId, "PENDING", status);
        return map("id", appealId, "status", status);
    }

    public PageResult<Map<String, Object>> adminLogs(Map<String, String> params) {
        JdbcTemplate jdbc = jdbc();
        int page = page(params);
        int size = size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE deleted_at IS NULL", Long.class);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, operation_type, target_type, target_id, content, create_time
                FROM admin_operation_log
                WHERE deleted_at IS NULL
                ORDER BY create_time DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "operationType", rs.getString("operation_type"),
                "targetType", rs.getString("target_type"),
                "targetId", rs.getObject("target_id"),
                "content", rs.getString("content"),
                "time", date(rs.getObject("create_time", LocalDateTime.class))
        ), (page - 1) * size, size);
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> disableMember(Long adminAccountId, Long memberId, Map<String, Object> request) {
        return updateMemberStatus(adminAccountId, memberId, "DISABLED", firstNonBlank(value(request, "reason", ""), "管理员禁用会员"));
    }

    public Map<String, Object> enableMember(Long adminAccountId, Long memberId) {
        return updateMemberStatus(adminAccountId, memberId, "NORMAL", "管理员启用会员");
    }

    public PageResult<Map<String, Object>> listCategories(Map<String, String> params) {
        JdbcTemplate jdbc = jdbc();
        int page = page(params);
        int size = size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String status = blankToNull(params.get("status"));
        String where = "WHERE deleted_at IS NULL" + (status == null ? "" : " AND status = ?");
        Object[] countArgs = status == null ? new Object[]{} : new Object[]{status};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM resource_category " + where, Long.class, countArgs);
        List<Object> args = new ArrayList<>(Arrays.asList(countArgs));
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, parent_id, category_name, level_no, status, sort_order, create_time
                FROM resource_category
                %s
                ORDER BY level_no ASC, sort_order ASC, id ASC
                LIMIT ?, ?
                """.formatted(where), (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "parentId", rs.getObject("parent_id"),
                "name", rs.getString("category_name"),
                "categoryName", rs.getString("category_name"),
                "level", rs.getInt("level_no"),
                "status", rs.getString("status"),
                "sortOrder", rs.getInt("sort_order"),
                "date", date(rs.getObject("create_time", LocalDateTime.class))
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> createCategory(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", 1L, "status", "ENABLED", "name", value(request, "name", ""));
        }
        return inTransaction(() -> {
            String name = firstNonBlank(value(request, "name", ""), value(request, "categoryName", ""));
            if (name.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名称不能为空");
            }
            Long parentId = number(firstPresent(request, "parentId"), 0L);
            int level = parentId == 0 ? 1 : 2;
            KeyHolder keyHolder = new GeneratedKeyHolder();
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
                statement.setInt(4, (int) number(firstPresent(request, "sortOrder"), 0L).longValue());
                return statement;
            }, keyHolder);
            Long id = key(keyHolder);
            adminLog(adminProfileId(adminAccountId), "CATEGORY_CREATE", "CATEGORY", id, null, "ENABLED");
            return category(id);
        });
    }

    public Map<String, Object> updateCategory(Long adminAccountId, Long categoryId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", categoryId, "request", request);
        }
        return inTransaction(() -> {
            Map<String, Object> before = category(categoryId);
            jdbc.update("""
                    UPDATE resource_category
                    SET category_name = COALESCE(?, category_name),
                        sort_order = COALESCE(?, sort_order),
                        status = COALESCE(?, status)
                    WHERE id = ? AND deleted_at IS NULL
                    """, nullable(request, "name"), firstPresent(request, "sortOrder"), nullable(request, "status"), categoryId);
            Map<String, Object> after = category(categoryId);
            adminLog(adminProfileId(adminAccountId), "CATEGORY_UPDATE", "CATEGORY", categoryId, String.valueOf(before.get("status")), String.valueOf(after.get("status")));
            return after;
        });
    }

    public Map<String, Object> disableCategory(Long adminAccountId, Long categoryId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", categoryId, "status", "DISABLED");
        }
        return inTransaction(() -> {
            Map<String, Object> before = category(categoryId);
            jdbc.update("UPDATE resource_category SET status = 'DISABLED' WHERE id = ? AND deleted_at IS NULL", categoryId);
            adminLog(adminProfileId(adminAccountId), "CATEGORY_DISABLE", "CATEGORY", categoryId, String.valueOf(before.get("status")), "DISABLED");
            return category(categoryId);
        });
    }

    public PageResult<Map<String, Object>> listTags(Map<String, String> params) {
        JdbcTemplate jdbc = jdbc();
        int page = page(params);
        int size = size(params);
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        String keyword = blankToNull(params.get("keyword"));
        String where = "WHERE deleted_at IS NULL" + (keyword == null ? "" : " AND tag_name LIKE ?");
        Object[] countArgs = keyword == null ? new Object[]{} : new Object[]{"%" + keyword + "%"};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM tag_info " + where, Long.class, countArgs);
        List<Object> args = new ArrayList<>(Arrays.asList(countArgs));
        args.add((page - 1) * size);
        args.add(size);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, tag_name, use_count, status, create_time
                FROM tag_info
                %s
                ORDER BY use_count DESC, id ASC
                LIMIT ?, ?
                """.formatted(where), (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "name", rs.getString("tag_name"),
                "tagName", rs.getString("tag_name"),
                "useCount", rs.getInt("use_count"),
                "status", rs.getString("status"),
                "date", date(rs.getObject("create_time", LocalDateTime.class))
        ), args.toArray());
        return new PageResult<>(total, list, page, size);
    }

    public Map<String, Object> createTag(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", 1L, "status", "ENABLED", "name", value(request, "name", ""));
        }
        return inTransaction(() -> {
            String name = firstNonBlank(value(request, "name", ""), value(request, "tagName", ""));
            if (name.isBlank() || name.length() > 12) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名称不能为空且最多 12 个字符");
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
            Long id = key(keyHolder);
            adminLog(adminProfileId(adminAccountId), "TAG_CREATE", "TAG", id, null, "ENABLED");
            return tag(id);
        });
    }

    public Map<String, Object> disableTag(Long adminAccountId, Long tagId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", tagId, "status", "DISABLED");
        }
        return inTransaction(() -> {
            Map<String, Object> before = tag(tagId);
            jdbc.update("UPDATE tag_info SET status = 'DISABLED' WHERE id = ? AND deleted_at IS NULL", tagId);
            adminLog(adminProfileId(adminAccountId), "TAG_DISABLE", "TAG", tagId, String.valueOf(before.get("status")), "DISABLED");
            return tag(tagId);
        });
    }

    public Map<String, Object> mergeTags(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("ok", true, "request", request);
        }
        return inTransaction(() -> {
            Long sourceId = number(firstPresent(request, "sourceTagId", "sourceId"), 0L);
            Long targetId = number(firstPresent(request, "targetTagId", "targetId"), 0L);
            if (sourceId == 0 || targetId == 0 || Objects.equals(sourceId, targetId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签合并参数不正确");
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
            adminLog(adminProfileId(adminAccountId), "TAG_MERGE", "TAG", targetId, String.valueOf(source.get("name")), String.valueOf(target.get("name")));
            return map("ok", true, "source", tag(sourceId), "target", tag(targetId));
        });
    }

    public Map<String, Object> systemConfig() {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("upload.max_file_size_mb", "100", "page.default_size", "20");
        }
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT config_key, config_value, value_type, description, is_sensitive, is_enabled
                FROM system_config
                WHERE is_enabled = 1
                ORDER BY id ASC
                """, (rs, rowNum) -> map(
                "key", rs.getString("config_key"),
                "value", rs.getInt("is_sensitive") == 1 ? "******" : rs.getString("config_value"),
                "valueType", rs.getString("value_type"),
                "description", rs.getString("description")
        ));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("key")), row.get("value"));
        }
        return result;
    }

    public Map<String, Object> updateSystemConfig(Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return request;
        }
        return inTransaction(() -> {
            Long adminId = adminProfileId(adminAccountId);
            if (request.containsKey("key")) {
                upsertConfig(jdbc, String.valueOf(request.get("key")), String.valueOf(request.getOrDefault("value", "")), String.valueOf(request.getOrDefault("valueType", "STRING")));
            } else {
                for (Map.Entry<String, Object> entry : request.entrySet()) {
                    if (entry.getValue() != null) {
                        upsertConfig(jdbc, entry.getKey(), String.valueOf(entry.getValue()), "STRING");
                    }
                }
            }
            adminLog(adminId, "SYSTEM_CONFIG_UPDATE", "SYSTEM_CONFIG", null, "UPDATED", "UPDATED");
            return systemConfig();
        });
    }

    public Map<String, Object> refreshCache(Long adminAccountId) {
        adminLog(adminProfileId(adminAccountId), "CACHE_REFRESH", "SYSTEM_CONFIG", null, "CACHE", "REFRESHED");
        return map("ok", true, "status", "REFRESHED");
    }

    public Map<String, Object> closeRequestByAdmin(Long adminAccountId, Long requestId, Map<String, Object> request) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", requestId, "status", "CLOSED");
        }
        return inTransaction(() -> {
            Map<String, Object> post = jdbc.queryForObject("""
                    SELECT requester_id, reward_points, status
                    FROM request_post
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map("requesterId", rs.getLong("requester_id"), "reward", rs.getInt("reward_points"), "status", rs.getString("status")), requestId);
            if (post == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "悬赏帖不存在");
            }
            String before = String.valueOf(post.get("status"));
            String reason = firstNonBlank(value(request, "reason", ""), "管理员强制关闭悬赏");
            RequestStateMachine.assertCanTransit(before, "CLOSED", "CLOSE", "ADMIN", false, reason);
            if ("ONGOING".equals(before)) {
                int reward = (int) number(post.get("reward"), 0L).longValue();
                if (reward > 0) {
                    pointManager.refundRequest(requestId);
                }
            }
            jdbc.update("UPDATE request_post SET status = 'CLOSED', closed_time = NOW(3) WHERE id = ?", requestId);
            jdbc.update("""
                    INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                    VALUES (?, ?, 'CLOSED', ?, ?)
                    """, requestId, before, adminAccountId, reason);
            adminLog(adminProfileId(adminAccountId), "REQUEST_CLOSE", "REQUEST_POST", requestId, before, "CLOSED");
            return requestPost(requestId);
        });
    }

    public Map<String, Object> deleteReplyByAdmin(Long adminAccountId, Long replyId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", replyId, "status", "DELETED");
        }
        return inTransaction(() -> {
            Map<String, Object> reply = jdbc.queryForObject("""
                    SELECT request_id, status
                    FROM request_reply
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map("requestId", rs.getLong("request_id"), "status", rs.getString("status")), replyId);
            if (reply == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "回复不存在");
            }
            jdbc.update("UPDATE request_reply SET status = 'DELETED', deleted_at = NOW(3) WHERE id = ?", replyId);
            jdbc.update("UPDATE request_post SET answer_count = GREATEST(answer_count - 1, 0) WHERE id = ?", number(reply.get("requestId"), 0L));
            adminLog(adminProfileId(adminAccountId), "REPLY_DELETE", "REQUEST_REPLY", replyId, String.valueOf(reply.get("status")), "DELETED");
            return map("id", replyId, "status", "DELETED");
        });
    }

    private Map<String, Object> updateMemberStatus(Long adminAccountId, Long memberId, String status, String reason) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", memberId, "status", status);
        }
        return inTransaction(() -> {
            Map<String, Object> account = jdbc.queryForObject("""
                    SELECT ua.id AS account_id, ua.role, ua.status
                    FROM member_profile mp
                    JOIN user_account ua ON ua.id = mp.account_id
                    WHERE mp.id = ? AND mp.deleted_at IS NULL AND ua.deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> map(
                    "accountId", rs.getLong("account_id"),
                    "role", rs.getString("role"),
                    "status", rs.getString("status")
            ), memberId);
            if (account == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "会员不存在");
            }
            if (!"USER".equals(account.get("role"))) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "不能通过会员接口操作管理员账号");
            }
            String before = String.valueOf(account.get("status"));
            jdbc.update("UPDATE user_account SET status = ? WHERE id = ?", status, number(account.get("accountId"), 0L));
            adminLog(adminProfileId(adminAccountId), "MEMBER_" + status, "MEMBER", memberId, before, status);
            notifyMember(memberId, "MEMBER_STATUS", "账号状态更新", "你的账号状态已变更为 " + status + "，原因：" + reason, "MEMBER", memberId);
            return map("id", memberId, "accountId", account.get("accountId"), "status", status, "reason", reason);
        });
    }

    private Map<String, Object> category(Long categoryId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", categoryId, "status", "ENABLED");
        }
        return jdbc.queryForObject("""
                SELECT id, parent_id, category_name, level_no, status, sort_order, create_time
                FROM resource_category
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "parentId", rs.getObject("parent_id"),
                "name", rs.getString("category_name"),
                "categoryName", rs.getString("category_name"),
                "level", rs.getInt("level_no"),
                "status", rs.getString("status"),
                "sortOrder", rs.getInt("sort_order"),
                "date", date(rs.getObject("create_time", LocalDateTime.class))
        ), categoryId);
    }

    private Map<String, Object> tag(Long tagId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", tagId, "status", "ENABLED");
        }
        return jdbc.queryForObject("""
                SELECT id, tag_name, use_count, status, create_time
                FROM tag_info
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "name", rs.getString("tag_name"),
                "tagName", rs.getString("tag_name"),
                "useCount", rs.getInt("use_count"),
                "status", rs.getString("status"),
                "date", date(rs.getObject("create_time", LocalDateTime.class))
        ), tagId);
    }

    private void upsertConfig(JdbcTemplate jdbc, String key, String value, String valueType) {
        String safeType = List.of("STRING", "INTEGER", "BOOLEAN", "JSON").contains(valueType) ? valueType : "STRING";
        jdbc.update("""
                INSERT INTO system_config(config_key, config_value, value_type, description, is_enabled)
                VALUES (?, ?, ?, '后台配置更新', 1)
                ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), value_type = VALUES(value_type), is_enabled = 1
                """, key, value, safeType);
    }

    private Map<String, Object> addComment(String targetType, Long targetId, String content, Long accountId, Long parentId, Long toMemberId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", 1L, "targetType", targetType, "targetId", targetId, "author", "demo_user", "content", content, "date", today(), "mine", true);
        }
        return inTransaction(() -> {
            Long memberId = requireMemberId(accountId);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO comment_info(target_type, target_id, member_id, parent_id, root_id, to_member_id, content, status)
                        VALUES (?, ?, ?, ?, NULL, ?, ?, 'ACTIVE')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, targetType);
                statement.setLong(2, targetId);
                statement.setLong(3, memberId);
                if (parentId == null || parentId == 0) {
                    statement.setObject(4, null);
                } else {
                    statement.setLong(4, parentId);
                }
                if (toMemberId == null || toMemberId == 0) {
                    statement.setObject(5, null);
                } else {
                    statement.setLong(5, toMemberId);
                }
                statement.setString(6, firstNonBlank(content, "评论内容"));
                return statement;
            }, keyHolder);
            Long commentId = key(keyHolder);
            Long rootId = parentId == null || parentId == 0 ? commentId : parentId;
            jdbc.update("UPDATE comment_info SET root_id = ? WHERE id = ?", rootId, commentId);
            if ("RESOURCE".equals(targetType)) {
                jdbc.update("UPDATE resource_info SET comment_count = comment_count + 1 WHERE id = ?", targetId);
                Long receiver = jdbc.queryForObject("SELECT publisher_id FROM resource_info WHERE id = ?", Long.class, targetId);
                if (!Objects.equals(receiver, memberId)) {
                    notifyMember(receiver, "COMMENT", "资源收到新评论", "你的资源收到一条新评论", "RESOURCE", targetId);
                }
            }
            if ("REQUEST_POST".equals(targetType)) {
                jdbc.update("UPDATE request_post SET comment_count = comment_count + 1 WHERE id = ?", targetId);
                Long receiver = jdbc.queryForObject("SELECT requester_id FROM request_post WHERE id = ?", Long.class, targetId);
                if (!Objects.equals(receiver, memberId)) {
                    notifyMember(receiver, "COMMENT", "求资源收到新评论", "你的求资源帖子收到一条新评论", "REQUEST_POST", targetId);
                }
            }
            return comment(commentId, accountId);
        });
    }

    private Map<String, Object> resource(Long resourceId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
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
                    """, resourceMapper(accountId), resourceId);
        } catch (DataAccessException ignored) {
            return defaultResource();
        }
    }

    private Map<String, Object> requestPost(Long requestId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return defaultRequest();
        }
        try {
            return jdbc.queryForObject("""
                    SELECT rp.*, mp.nickname AS author_name,
                           c2.id AS category2_id, c2.category_name AS category2_name,
                           c1.id AS category1_id, c1.category_name AS category1_name
                    FROM request_post rp
                    JOIN member_profile mp ON mp.id = rp.requester_id
                    LEFT JOIN resource_category c2 ON c2.id = rp.category_id
                    LEFT JOIN resource_category c1 ON c1.id = c2.parent_id
                    WHERE rp.id = ? AND rp.deleted_at IS NULL
                    """, requestMapper(), requestId);
        } catch (DataAccessException ignored) {
            return defaultRequest();
        }
    }

    private Map<String, Object> reply(Long replyId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", replyId, "accepted", false);
        }
        return jdbc.queryForObject("""
                SELECT rr.*, mp.nickname AS author_name
                FROM request_reply rr
                JOIN member_profile mp ON mp.id = rr.replier_id
                WHERE rr.id = ?
                """, replyMapper(), replyId);
    }

    private PageResult<Map<String, Object>> comments(String targetType, Long targetId, Long accountId, int page, int size) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), page, size);
        }
        try {
            long total = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM comment_info
                    WHERE target_type = ? AND target_id = ? AND status = 'ACTIVE' AND parent_id IS NULL AND deleted_at IS NULL
                    """, Long.class, targetType, targetId);
            List<Map<String, Object>> list = jdbc.query("""
                    SELECT ci.id, ci.target_type, ci.target_id, ci.content, ci.create_time, ci.member_id, ci.parent_id, mp.nickname
                    FROM comment_info ci
                    JOIN member_profile mp ON mp.id = ci.member_id
                    WHERE ci.target_type = ? AND ci.target_id = ? AND ci.status = 'ACTIVE' AND ci.parent_id IS NULL AND ci.deleted_at IS NULL
                    ORDER BY ci.create_time DESC
                    LIMIT ?, ?
                    """, commentMapper(accountId), targetType, targetId, (page - 1) * size, size);
            return new PageResult<>(total, list, page, size);
        } catch (DataAccessException ignored) {
            return new PageResult<>(0, List.of(), page, size);
        }
    }

    private Map<String, Object> comment(Long commentId, Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return map("id", commentId, "content", "", "date", today(), "mine", true);
        }
        return jdbc.queryForObject("""
                SELECT ci.id, ci.target_type, ci.target_id, ci.content, ci.create_time, ci.member_id, ci.parent_id, mp.nickname
                FROM comment_info ci
                JOIN member_profile mp ON mp.id = ci.member_id
                WHERE ci.id = ?
                """, commentMapper(accountId), commentId);
    }

    private RowMapper<Map<String, Object>> userMapper() {
        return (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "memberId", rs.getObject("member_id"),
                "username", rs.getString("username"),
                "nickname", firstNonBlank(rs.getString("nickname"), rs.getString("username")),
                "email", rs.getString("email"),
                "role", roleForToken(rs.getString("role")),
                "status", "NORMAL",
                "emailVerified", true,
                "bio", firstNonBlank(rs.getString("bio"), ""),
                "contact", rs.getString("email"),
                "avatar", firstNonBlank(rs.getString("avatar_url"), ""),
                "level", firstNonBlank(rs.getString("level_name"), "普通会员"),
                "points", rs.getInt("current_points"),
                "frozenPoints", rs.getInt("frozen_points"),
                "expNeeded", Math.max(0, 1000 - rs.getInt("current_points")),
                "passwordUpdatedAt", date(rs.getObject("password_changed_time", LocalDateTime.class))
        );
    }

    private RowMapper<Map<String, Object>> resourceMapper(Long accountId) {
        return (rs, rowNum) -> {
            Long id = rs.getLong("id");
            Long memberId = memberId(accountId);
            List<Map<String, Object>> attachments = attachments(id);
            return map(
                    "id", id,
                    "title", rs.getString("title"),
                    "summary", rs.getString("summary"),
                    "description", rs.getString("summary"),
                    "detail", rs.getString("description"),
                    "categoryId", rs.getObject("category2_id"),
                    "category1", stringId(rs.getObject("category1_id")),
                    "category2", stringId(rs.getObject("category2_id")),
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
                    "date", date(rs.getObject("create_time", LocalDateTime.class)),
                    "publishedAt", date(rs.getObject("published_time", LocalDateTime.class)),
                    "tags", resourceTags(id),
                    "attachments", attachments,
                    "fileName", attachments.isEmpty() ? "" : attachments.get(0).get("name"),
                    "fileSize", attachments.isEmpty() ? "" : attachments.get(0).get("size"),
                    "liked", interactionActive(memberId, "RESOURCE", id, "LIKE"),
                    "favorited", interactionActive(memberId, "RESOURCE", id, "FAVORITE"),
                    "userRating", userRating(memberId, id)
            );
        };
    }

    private RowMapper<Map<String, Object>> requestMapper() {
        return (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "title", rs.getString("title"),
                "content", rs.getString("content"),
                "description", rs.getString("content"),
                "categoryId", rs.getObject("category2_id"),
                "category1", stringId(rs.getObject("category1_id")),
                "category2", stringId(rs.getObject("category2_id")),
                "rewardPoints", rs.getInt("reward_points"),
                "points", rs.getInt("reward_points"),
                "replyCount", rs.getInt("answer_count"),
                "commentCount", rs.getInt("comment_count"),
                "author", rs.getString("author_name"),
                "date", date(rs.getObject("create_time", LocalDateTime.class)),
                "status", rs.getString("status"),
                "tags", requestTags(rs.getLong("id")),
                "expectedFormat", firstNonBlank(rs.getString("expected_format"), "不限"),
                "format", firstNonBlank(rs.getString("expected_format"), "不限")
        );
    }

    private RowMapper<Map<String, Object>> replyMapper() {
        return (rs, rowNum) -> map(
                "id", rs.getLong("id"),
                "requestId", rs.getLong("request_id"),
                "author", rs.getString("author_name"),
                "content", rs.getString("content"),
                "resourceId", rs.getObject("resource_id"),
                "externalUrl", rs.getString("external_url"),
                "accepted", rs.getInt("is_accepted") == 1,
                "date", date(rs.getObject("create_time", LocalDateTime.class))
        );
    }

    private RowMapper<Map<String, Object>> commentMapper(Long accountId) {
        return (rs, rowNum) -> {
            Long memberId = memberId(accountId);
            return map(
                    "id", rs.getLong("id"),
                    "targetType", rs.getString("target_type"),
                    "targetId", rs.getLong("target_id"),
                    "parentId", rs.getObject("parent_id"),
                    "author", rs.getString("nickname"),
                    "content", rs.getString("content"),
                    "date", date(rs.getObject("create_time", LocalDateTime.class)),
                    "mine", Objects.equals(rs.getLong("member_id"), memberId),
                    "replies", List.of()
            );
        };
    }

    private List<Map<String, Object>> attachments(Long resourceId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return List.of();
        }
        try {
            return jdbc.query("""
                    SELECT id, original_file_name, file_ext, file_size, download_count
                    FROM file_attachment
                    WHERE owner_type = 'RESOURCE' AND owner_id = ? AND status = 'NORMAL' AND deleted_at IS NULL
                    ORDER BY id
                    """, (rs, rowNum) -> map(
                    "id", rs.getLong("id"),
                    "name", rs.getString("original_file_name"),
                    "fileName", rs.getString("original_file_name"),
                    "size", readableSize(rs.getLong("file_size")),
                    "fileSize", rs.getLong("file_size"),
                    "type", firstNonBlank(rs.getString("file_ext"), "file").toUpperCase(),
                    "downloads", rs.getInt("download_count")
            ), resourceId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private List<String> resourceTags(Long resourceId) {
        return tags("""
                SELECT ti.tag_name
                FROM resource_tag_rel rtr
                JOIN tag_info ti ON ti.id = rtr.tag_id
                WHERE rtr.resource_id = ?
                ORDER BY ti.id
                """, resourceId);
    }

    private List<String> requestTags(Long requestId) {
        return tags("""
                SELECT ti.tag_name
                FROM request_tag_rel rtr
                JOIN tag_info ti ON ti.id = rtr.tag_id
                WHERE rtr.request_id = ?
                ORDER BY ti.id
                """, requestId);
    }

    private List<String> tags(String sql, Long id) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return List.of();
        }
        try {
            return jdbc.queryForList(sql, String.class, id);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private void insertResourceTags(JdbcTemplate jdbc, Long resourceId, String tagsText) {
        for (String tag : splitTags(tagsText)) {
            Long tagId = ensureTag(jdbc, tag);
            jdbc.update("INSERT IGNORE INTO resource_tag_rel(resource_id, tag_id) VALUES (?, ?)", resourceId, tagId);
        }
    }

    private void insertRequestTags(JdbcTemplate jdbc, Long requestId, String tagsText) {
        for (String tag : splitTags(tagsText)) {
            Long tagId = ensureTag(jdbc, tag);
            jdbc.update("INSERT IGNORE INTO request_tag_rel(request_id, tag_id) VALUES (?, ?)", requestId, tagId);
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
            return key(keyHolder);
        }
    }

    private void insertAttachments(JdbcTemplate jdbc, String ownerType, Long ownerId, Long accountId, List<MultipartFile> files, String fallbackName) {
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (safeFiles.isEmpty()) {
            insertAttachment(jdbc, ownerType, ownerId, accountId, fallbackName, "application/octet-stream", 0);
            return;
        }
        for (MultipartFile file : safeFiles) {
            insertAttachment(jdbc, ownerType, ownerId, accountId, firstNonBlank(file.getOriginalFilename(), "uploaded-file"), file.getContentType(), file.getSize());
        }
    }

    private void insertAttachment(JdbcTemplate jdbc, String ownerType, Long ownerId, Long accountId, String fileName, String contentType, long size) {
        jdbc.update("""
                INSERT INTO file_attachment(owner_type, owner_id, uploader_id, original_file_name, stored_file_name, file_ext, mime_type, file_size, storage_path, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NORMAL')
                """, ownerType, ownerId, accountId, fileName, ownerType.toLowerCase() + "-" + ownerId + "-" + fileName,
                fileExt(fileName), contentType, size, "./uploads/" + ownerType.toLowerCase() + "/" + ownerId + "/" + fileName);
    }

    private void recordLogin(JdbcTemplate jdbc, Long accountId, String loginAccount, String result, String failReason) {
        jdbc.update("""
                INSERT INTO login_record(account_id, login_account, result, fail_reason)
                VALUES (?, ?, ?, ?)
                """, accountId, loginAccount, result, failReason);
    }

    private Long memberId(Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (accountId == null) {
            return null;
        }
        if (jdbc == null) {
            return DEFAULT_MEMBER_ID;
        }
        try {
            return jdbc.queryForObject("""
                    SELECT mp.id
                    FROM member_profile mp
                    JOIN user_account ua ON ua.id = mp.account_id
                    WHERE mp.account_id = ? AND mp.deleted_at IS NULL
                      AND ua.status = 'NORMAL' AND ua.deleted_at IS NULL
                    """, Long.class, accountId);
        } catch (DataAccessException ignored) {
            return null;
        }
    }

    private Long requireMemberId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后再操作");
        }
        Long memberId = memberId(accountId);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在、已禁用或不是会员账号");
        }
        return memberId;
    }

    private Long adminProfileId(Long accountId) {
        JdbcTemplate jdbc = jdbc();
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录管理员账号");
        }
        if (jdbc == null) {
            return 1L;
        }
        try {
            return jdbc.queryForObject("""
                    SELECT ap.id
                    FROM administrator_profile ap
                    JOIN user_account ua ON ua.id = ap.account_id
                    WHERE ap.account_id = ? AND ap.deleted_at IS NULL
                      AND ua.status = 'NORMAL' AND ua.deleted_at IS NULL
                      AND ua.role IN ('ADMIN', 'SUPER_ADMIN', 'AUDITOR')
                    """, Long.class, accountId);
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
    }

    private void adminLog(Long adminProfileId, String operationType, String targetType, Long targetId, String before, String after) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO admin_operation_log(admin_id, operation_type, target_type, target_id, content, before_snapshot, after_snapshot)
                VALUES (?, ?, ?, ?, ?, JSON_OBJECT('status', ?), JSON_OBJECT('status', ?))
                """, adminProfileId, operationType, targetType, targetId, operationType, before, after);
    }

    private void notifyMember(Long memberId, String type, String title, String content, String targetType, Long targetId) {
        try {
            notificationService.createForMember(memberId, type, title, content, targetType, targetId);
        } catch (RuntimeException ignored) {
            JdbcTemplate jdbc = jdbc();
            if (jdbc != null) {
                jdbc.update("""
                        INSERT INTO notification_event(event_type, source_type, source_id, receiver_id, payload, status, fail_reason, process_time)
                        VALUES (?, ?, ?, ?, JSON_OBJECT('title', ?, 'content', ?), 'FAILED', '通知写入失败', NOW(3))
                        """, type, targetType, targetId, memberId, title, content);
            }
        }
    }

    private static String auditResultForAction(String action, String targetStatus) {
        return switch (targetStatus) {
            case "PUBLISHED" -> "PUBLISHED".equals(action) || "APPROVE".equals(action) || "APPROVED".equals(action) ? "APPROVED" : "RESTORED";
            case "REJECTED" -> "REJECTED";
            case "REVIEWING_RISK" -> "RISK_REVIEW";
            case "COPYRIGHT_DOWN" -> "COPYRIGHT_DOWN";
            case "DELETED" -> "DELETED";
            default -> "OFFLINE";
        };
    }

    private static String defaultResourceReason(String action) {
        return switch (firstNonBlank(action)) {
            case "APPROVE", "APPROVED" -> "审核通过";
            case "REJECT", "REJECTED" -> "审核驳回";
            case "RISK", "RISK_REVIEW", "REVIEWING_RISK" -> "进入风险核查";
            case "COPYRIGHT", "COPYRIGHT_DOWN" -> "版权投诉处理下架";
            case "RESTORE", "RESTORED", "RISK_CLEAR", "COPYRIGHT_CLEAR" -> "核查通过恢复发布";
            case "DELETE", "DELETED" -> "管理员删除资源";
            default -> "管理员下架资源";
        };
    }

    private boolean interactionActive(Long memberId, String targetType, Long targetId, String actionType) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null || memberId == null) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM user_interaction
                    WHERE member_id = ? AND target_type = ? AND target_id = ? AND action_type = ? AND status = 'ACTIVE'
                    """, Integer.class, memberId, targetType, targetId, actionType);
            return count != null && count > 0;
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    private int userRating(Long memberId, Long resourceId) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null || memberId == null) {
            return 0;
        }
        try {
            Integer score = jdbc.queryForObject("SELECT score FROM resource_rating WHERE member_id = ? AND resource_id = ?", Integer.class, memberId, resourceId);
            return score == null ? 0 : score;
        } catch (DataAccessException ignored) {
            return 0;
        }
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            return supplier.get();
        }
        return new TransactionTemplate(transactionManager).execute(status -> supplier.get());
    }

    private JdbcTemplate jdbc() {
        return jdbcProvider.getIfAvailable();
    }

    private Map<String, Object> tokenResponse(Map<String, Object> user, String role) {
        Long accountId = number(user.get("id"), DEFAULT_ACCOUNT_ID);
        String token = jwtService.generate(String.valueOf(accountId), role);
        return map(
                "token", token,
                "user", user,
                "role", role,
                "expireAt", Instant.now().plusSeconds(jwtProperties.expiresMinutes() * 60).toString()
        );
    }

    private Map<String, Object> defaultUser(Long accountId) {
        return map(
                "id", accountId == null ? DEFAULT_ACCOUNT_ID : accountId,
                "memberId", DEFAULT_MEMBER_ID,
                "username", "demo_user",
                "nickname", "考研资料君",
                "email", "demo@example.com",
                "role", "MEMBER",
                "status", "NORMAL",
                "emailVerified", true,
                "bio", "热爱分享学习资料。",
                "contact", "demo@example.com",
                "avatar", "",
                "level", "优质会员",
                "points", 650,
                "frozenPoints", 0,
                "expNeeded", 350,
                "passwordUpdatedAt", today()
        );
    }

    private Map<String, Object> defaultResource() {
        return map(
                "id", 1L,
                "title", "2026考研政治历年真题完整版",
                "summary", "整理近年考研政治真题和答案解析，适合课程项目演示。",
                "description", "整理近年考研政治真题和答案解析，适合课程项目演示。",
                "detail", "该资源用于演示资源发布、审核通过、附件下载、收藏点赞、评论评分等核心流程。",
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
                "date", today(),
                "publishedAt", today(),
                "tags", List.of("考研", "政治", "真题"),
                "attachments", List.of(map("id", 1L, "name", "kaoyan-politics-2026.zip", "fileName", "kaoyan-politics-2026.zip", "size", "2.0 MB", "type", "ZIP", "downloads", 136)),
                "fileName", "kaoyan-politics-2026.zip",
                "fileSize", "2.0 MB",
                "liked", false,
                "favorited", false,
                "userRating", 0
        );
    }

    private Map<String, Object> defaultRequest() {
        return map(
                "id", 1L,
                "title", "求一份Spring Boot后端项目模板",
                "content", "需要一份适合课程设计使用的Spring Boot后端项目模板，最好包含登录鉴权、资源管理和基础测试。",
                "description", "需要一份适合课程设计使用的Spring Boot后端项目模板，最好包含登录鉴权、资源管理和基础测试。",
                "categoryId", 32L,
                "category1", "3",
                "category2", "32",
                "rewardPoints", 50,
                "points", 50,
                "replyCount", 0,
                "commentCount", 0,
                "author", "考研资料君",
                "date", today(),
                "status", "ONGOING",
                "tags", List.of("Java", "SpringBoot"),
                "expectedFormat", "zip或Git仓库链接",
                "format", "zip或Git仓库链接"
        );
    }

    private static int page(Map<String, String> params) {
        return Math.max(1, intValue(params.get("page"), 1));
    }

    private static int size(Map<String, String> params) {
        return Math.max(1, Math.min(100, intValue(firstNonBlank(params.get("size"), params.get("pageSize")), 20)));
    }

    private static Long key(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    private static Object firstPresent(Map<String, Object> request, String... keys) {
        if (request == null) {
            return null;
        }
        for (String key : keys) {
            if (request.containsKey(key) && request.get(key) != null) {
                return request.get(key);
            }
        }
        return null;
    }

    private static String nullable(Map<String, Object> request, String key) {
        if (request == null || request.get(key) == null || String.valueOf(request.get(key)).isBlank()) {
            return null;
        }
        return String.valueOf(request.get(key));
    }

    private static String value(Map<String, Object> request, String key, String fallback) {
        Object value = request == null ? null : request.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static Long number(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intValue(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Long longValue(String value, Long fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> splitTags(String tagsText) {
        if (tagsText == null || tagsText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagsText.split("[,，]"))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .limit(5)
                .collect(Collectors.toList());
    }

    private static String today() {
        return LocalDate.now().format(DATE);
    }

    private static String date(LocalDateTime time) {
        return time == null ? today() : time.toLocalDate().format(DATE);
    }

    private static String stringId(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String readableSize(long bytes) {
        if (bytes <= 0) {
            return "待处理";
        }
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + " KB";
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static String fileExt(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "file";
    }

    private static String resourceType(String display) {
        return switch (firstNonBlank(display)) {
            case "软件" -> "SOFTWARE";
            case "源码" -> "SOURCE_CODE";
            case "素材" -> "MATERIAL";
            case "教程" -> "COURSE";
            case "模板" -> "TEMPLATE";
            case "链接" -> "LINK";
            default -> firstNonBlank(display).matches("[A-Z_]+") ? firstNonBlank(display) : "DOCUMENT";
        };
    }

    private static String displayResourceType(String type) {
        return switch (firstNonBlank(type)) {
            case "SOFTWARE" -> "软件";
            case "SOURCE_CODE" -> "源码";
            case "MATERIAL" -> "素材";
            case "COURSE" -> "教程";
            case "TEMPLATE" -> "模板";
            case "LINK" -> "链接";
            default -> "文档";
        };
    }

    private static String roleForToken(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || "AUDITOR".equals(role) ? "ADMIN" : "MEMBER";
    }

    private static String reportTarget(String target) {
        return switch (firstNonBlank(target)) {
            case "DEMAND", "REQUEST", "REQUEST_POST" -> "REQUEST_POST";
            case "COMMENT" -> "COMMENT";
            case "REQUEST_REPLY" -> "REQUEST_REPLY";
            case "USER" -> "USER";
            default -> "RESOURCE";
        };
    }

    private static String reportType(String type, String targetType) {
        if ("COPYRIGHT".equals(type)) {
            return "COPYRIGHT";
        }
        return switch (firstNonBlank(type)) {
            case "COMMENT" -> "COMMENT";
            case "REQUEST", "DEMAND", "REQUEST_POST" -> "REQUEST_POST";
            case "REQUEST_REPLY" -> "REQUEST_REPLY";
            case "USER" -> "USER";
            case "COPYRIGHT" -> "COPYRIGHT";
            default -> targetType;
        };
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }
}

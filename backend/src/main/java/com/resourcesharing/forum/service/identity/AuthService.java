package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.security.JwtProperties;
import com.resourcesharing.forum.security.JwtService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service("designSpecAuthService")
public class AuthService {
    private static final long DEFAULT_ACCOUNT_ID = 1L;
    private static final long DEFAULT_MEMBER_ID = 1L;
    private static final String ACCOUNT_TEMPORARILY_LOCKED = "\u7490\ufe40\u5f7f\u6d93\u5b58\u6902\u95bf\u4f78\u757e";

    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            TxSupport txSupport,
            ValueSupport values,
            MappingSupport mappings,
            JwtService jwtService,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> login(Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String account = values.value(request, "account", values.value(request, "username", ""));
        String password = values.value(request, "password", "");
        if (jdbc == null) {
            return tokenResponse(defaultUser(DEFAULT_ACCOUNT_ID), "MEMBER");
        }
        return txSupport.required(() -> {
            try {
                Map<String, Object> row = jdbc.queryForObject("""
                        SELECT id, username, email, password_hash, role, status, failed_login_count, locked_until
                        FROM user_account
                        WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                        FOR UPDATE
                        """, (rs, rowNum) -> values.map(
                        "id", rs.getLong("id"),
                        "username", rs.getString("username"),
                        "email", rs.getString("email"),
                        "passwordHash", rs.getString("password_hash"),
                        "role", rs.getString("role"),
                        "status", rs.getString("status"),
                        "failedLoginCount", rs.getInt("failed_login_count"),
                        "lockedUntil", rs.getObject("locked_until", LocalDateTime.class)
                ), account, account);
                Long accountId = values.number(row.get("id"), DEFAULT_ACCOUNT_ID);
                String status = String.valueOf(row.get("status"));
                if ("DISABLED".equals(status) || "DELETED".equals(status)) {
                    recordLogin(jdbc, accountId, account, "FAILED", "ACCOUNT_DISABLED");
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, "account disabled");
                }
                LocalDateTime lockedUntil = (LocalDateTime) row.get("lockedUntil");
                if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
                    recordLogin(jdbc, accountId, account, "FAILED", "ACCOUNT_LOCKED");
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, ACCOUNT_TEMPORARILY_LOCKED);
                }
                String hash = String.valueOf(row.get("passwordHash"));
                if (!passwordEncoder.matches(password, hash)) {
                    int nextFailCount = (int) values.number(row.get("failedLoginCount"), 0L).longValue() + 1;
                    jdbc.update("""
                            UPDATE user_account
                            SET failed_login_count = ?,
                                locked_until = IF(? >= 5, DATE_ADD(NOW(3), INTERVAL 10 MINUTE), locked_until)
                            WHERE id = ?
                            """, nextFailCount, nextFailCount, accountId);
                    recordLogin(jdbc, accountId, account, "FAILED", "PASSWORD_ERROR");
                    throw new BusinessException(ErrorCode.UNAUTHORIZED, nextFailCount >= 5 ? ACCOUNT_TEMPORARILY_LOCKED : "account or password is incorrect");
                }
                jdbc.update("""
                        UPDATE user_account
                        SET failed_login_count = 0, locked_until = NULL, last_login_time = NOW(3),
                            status = IF(status = 'LOCKED', 'NORMAL', status)
                        WHERE id = ?
                        """, accountId);
                recordLogin(jdbc, accountId, account, "SUCCESS", null);
                return tokenResponse(userProfile(jdbc, accountId), roleForToken(String.valueOf(row.get("role"))));
            } catch (BusinessException exception) {
                throw exception;
            } catch (DataAccessException exception) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "account or password is incorrect");
            }
        });
    }

    public Map<String, Object> register(Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String username = values.value(request, "username", "");
        String email = values.value(request, "email", "");
        String password = values.value(request, "password", "");
        if (jdbc == null) {
            return tokenResponse(defaultUser(DEFAULT_ACCOUNT_ID), "MEMBER");
        }
        if (username.isBlank() || email.isBlank() || !email.contains("@") || password.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "username, email and password are required");
        }
        return txSupport.required(() -> {
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
            Long accountId = values.key(accountKey);
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
            Long memberId = values.key(memberKey);
            jdbc.update("""
                    INSERT INTO member_point_account(member_id, level_id, current_points, frozen_points, total_earned_points, total_spent_points)
                    VALUES (?, 1, 0, 0, 0, 0)
                    """, memberId);
            jdbc.update("""
                    INSERT INTO point_flow(member_id, flow_type, scene, points_change, frozen_change,
                                           before_points, after_points, before_frozen_points, after_frozen_points,
                                           related_type, related_id, operator_id, description)
                    VALUES (?, 'EARN', 'REGISTER', 0, 0, 0, 0, 0, 0, 'USER_ACCOUNT', ?, ?, 'Register point account initialization')
                    """, memberId, accountId, accountId);
            return tokenResponse(userProfile(jdbc, accountId), "MEMBER");
        });
    }

    public Map<String, Object> requestResetPasswordCode(Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String account = values.firstNonBlank(values.value(request, "account", ""), values.value(request, "email", ""));
        if (jdbc == null) {
            return values.map("ok", true, "devCode", "000000");
        }
        try {
            Map<String, Object> row = jdbc.queryForObject("""
                    SELECT id, email
                    FROM user_account
                    WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                    """, (rs, rowNum) -> values.map("id", rs.getLong("id"), "email", rs.getString("email")), account, account);
            String code = "000000";
            jdbc.update("""
                    INSERT INTO email_verification_code(account_id, email, scene, code_hash, status, expire_time)
                    VALUES (?, ?, 'RESET_PASSWORD', ?, 'UNUSED', DATE_ADD(NOW(3), INTERVAL 10 MINUTE))
                    """, row.get("id"), row.get("email"), passwordEncoder.encode(code));
            return values.map("ok", true, "email", row.get("email"), "devCode", code, "expiresInMinutes", 10);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "account does not exist");
        }
    }

    public Map<String, Object> resetPassword(Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String account = values.firstNonBlank(values.value(request, "account", ""), values.value(request, "email", ""));
        String code = values.value(request, "code", "");
        String password = values.firstNonBlank(values.value(request, "newPassword", ""), values.value(request, "password", ""));
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "new password length must be at least 6");
        }
        if (jdbc == null) {
            return values.map("ok", true);
        }
        return txSupport.required(() -> {
            try {
                Map<String, Object> row = jdbc.queryForObject("""
                        SELECT id, email
                        FROM user_account
                        WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                        FOR UPDATE
                        """, (rs, rowNum) -> values.map("id", rs.getLong("id"), "email", rs.getString("email")), account, account);
                List<Map<String, Object>> codes = jdbc.query("""
                        SELECT id, code_hash
                        FROM email_verification_code
                        WHERE account_id = ? AND scene = 'RESET_PASSWORD' AND status = 'UNUSED' AND expire_time > NOW(3)
                        ORDER BY id DESC
                        LIMIT 1
                        """, (rs, rowNum) -> values.map("id", rs.getLong("id"), "hash", rs.getString("code_hash")), row.get("id"));
                if (codes.isEmpty() || !passwordEncoder.matches(code, String.valueOf(codes.get(0).get("hash")))) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "verification code is invalid or expired");
                }
                jdbc.update("UPDATE email_verification_code SET status = 'USED', used_time = NOW(3) WHERE id = ?", codes.get(0).get("id"));
                jdbc.update("""
                        UPDATE user_account
                        SET password_hash = ?, password_changed_time = NOW(3), failed_login_count = 0, locked_until = NULL,
                            status = IF(status = 'LOCKED', 'NORMAL', status)
                        WHERE id = ?
                        """, passwordEncoder.encode(password), row.get("id"));
                return values.map("ok", true, "email", row.get("email"));
            } catch (BusinessException exception) {
                throw exception;
            } catch (DataAccessException exception) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "account does not exist");
            }
        });
    }

    private Map<String, Object> userProfile(JdbcTemplate jdbc, Long accountId) {
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
                    """, mappings.userMapper(), accountId);
        } catch (DataAccessException ignored) {
            return defaultUser(accountId);
        }
    }

    private void recordLogin(JdbcTemplate jdbc, Long accountId, String loginAccount, String result, String failReason) {
        jdbc.update("""
                INSERT INTO login_record(account_id, login_account, result, fail_reason)
                VALUES (?, ?, ?, ?)
                """, accountId, loginAccount, result, failReason);
    }

    private Map<String, Object> tokenResponse(Map<String, Object> user, String role) {
        Long accountId = values.number(user.get("id"), DEFAULT_ACCOUNT_ID);
        String token = jwtService.generate(String.valueOf(accountId), role);
        return values.map(
                "token", token,
                "user", user,
                "role", role,
                "expireAt", Instant.now().plusSeconds(jwtProperties.expiresMinutes() * 60).toString()
        );
    }

    private Map<String, Object> defaultUser(Long accountId) {
        return values.map(
                "id", accountId == null ? DEFAULT_ACCOUNT_ID : accountId,
                "memberId", DEFAULT_MEMBER_ID,
                "username", "demo_user",
                "nickname", "demo_user",
                "email", "demo@example.com",
                "role", "MEMBER",
                "status", "NORMAL",
                "emailVerified", true,
                "bio", "demo user profile",
                "contact", "demo@example.com",
                "avatar", "",
                "level", "Member",
                "points", 650,
                "frozenPoints", 0,
                "expNeeded", 350,
                "passwordUpdatedAt", values.today()
        );
    }

    private String roleForToken(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || "AUDITOR".equals(role) ? "ADMIN" : "MEMBER";
    }
}

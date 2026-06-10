package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.security.JwtProperties;
import com.resourcesharing.forum.security.JwtService;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.point.PointRuleService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service("designSpecAuthService")
public class AuthService {
    private static final long DEFAULT_ACCOUNT_ID = 1L;
    private static final long DEFAULT_MEMBER_ID = 1L;
    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private static final String ACCOUNT_TEMPORARILY_LOCKED = "\u8d26\u53f7\u4e34\u65f6\u9501\u5b9a";

    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final EmailCodeService emailCodeService;
    private final PointManager pointManager;
    private final PointRuleService pointRules;

    public AuthService(
            TxSupport txSupport,
            ValueSupport values,
            MappingSupport mappings,
            JwtService jwtService,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder,
            EmailCodeService emailCodeService,
            PointManager pointManager,
            PointRuleService pointRules
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.emailCodeService = emailCodeService;
        this.pointManager = pointManager;
        this.pointRules = pointRules;
    }

    public Map<String, Object> login(Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String account = values.value(request, "account", values.value(request, "username", ""));
        String password = values.value(request, "password", "");
        if (jdbc == null) {
            return tokenResponse(defaultUser(DEFAULT_ACCOUNT_ID), "MEMBER");
        }
        LoginResult result = txSupport.required(() -> {
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
                    return LoginResult.failure("account disabled");
                }
                LocalDateTime lockedUntil = (LocalDateTime) row.get("lockedUntil");
                int failedLoginCount = (int) values.number(row.get("failedLoginCount"), 0L).longValue();
                LocalDateTime now = LocalDateTime.now();
                if (lockExpired(status, lockedUntil, now)) {
                    jdbc.update("""
                            UPDATE user_account
                            SET status = 'NORMAL', failed_login_count = 0, locked_until = NULL
                            WHERE id = ?
                            """, accountId);
                    status = "NORMAL";
                    failedLoginCount = 0;
                    lockedUntil = null;
                }
                if (isLocked(status, lockedUntil, failedLoginCount, now)) {
                    ensureLockPersisted(jdbc, accountId, lockedUntil);
                    recordLogin(jdbc, accountId, account, "LOCKED", "ACCOUNT_LOCKED");
                    return LoginResult.failure(ACCOUNT_TEMPORARILY_LOCKED);
                }
                String hash = String.valueOf(row.get("passwordHash"));
                if (!passwordEncoder.matches(password, hash)) {
                    int nextFailCount = failedLoginCount + 1;
                    jdbc.update("""
                            UPDATE user_account
                            SET failed_login_count = ?,
                                status = IF(? >= ?, 'LOCKED', status),
                                locked_until = IF(? >= ?, DATE_ADD(NOW(3), INTERVAL 10 MINUTE), locked_until)
                            WHERE id = ?
                            """, nextFailCount, nextFailCount, MAX_FAILED_LOGIN_COUNT,
                            nextFailCount, MAX_FAILED_LOGIN_COUNT, accountId);
                    boolean lockedByThisAttempt = nextFailCount >= MAX_FAILED_LOGIN_COUNT;
                    recordLogin(jdbc, accountId, account, lockedByThisAttempt ? "LOCKED" : "FAILED",
                            lockedByThisAttempt ? "ACCOUNT_LOCKED" : "PASSWORD_ERROR");
                    return LoginResult.failure(
                            lockedByThisAttempt ? ACCOUNT_TEMPORARILY_LOCKED : "account or password is incorrect");
                }
                jdbc.update("""
                        UPDATE user_account
                        SET failed_login_count = 0, locked_until = NULL, last_login_time = NOW(3),
                            status = IF(status = 'LOCKED', 'NORMAL', status)
                        WHERE id = ?
                        """, accountId);
                recordLogin(jdbc, accountId, account, "SUCCESS", null);
                Map<String, Object> profile = userProfile(jdbc, accountId);
                profile = rewardDailyLogin(jdbc, accountId, profile);
                return LoginResult.success(tokenResponse(profile, roleForToken(String.valueOf(row.get("role")))));
            } catch (DataAccessException exception) {
                return LoginResult.failure("account or password is incorrect");
            }
        });
        if (!result.success()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, result.message());
        }
        return result.data();
    }

    private record LoginResult(boolean success, String message, Map<String, Object> data) {
        private static LoginResult success(Map<String, Object> data) {
            return new LoginResult(true, "success", data);
        }

        private static LoginResult failure(String message) {
            return new LoginResult(false, message, null);
        }
    }

    private boolean isLocked(String status, LocalDateTime lockedUntil, int failedLoginCount, LocalDateTime now) {
        return "LOCKED".equals(status)
                || (lockedUntil != null && lockedUntil.isAfter(now))
                || failedLoginCount >= MAX_FAILED_LOGIN_COUNT;
    }

    private boolean lockExpired(String status, LocalDateTime lockedUntil, LocalDateTime now) {
        return "LOCKED".equals(status) && lockedUntil != null && !lockedUntil.isAfter(now);
    }

    private void ensureLockPersisted(JdbcTemplate jdbc, Long accountId, LocalDateTime lockedUntil) {
        jdbc.update("""
                UPDATE user_account
                SET status = 'LOCKED',
                    failed_login_count = GREATEST(failed_login_count, ?),
                    locked_until = IF(? IS NULL OR ? <= NOW(3), DATE_ADD(NOW(3), INTERVAL 10 MINUTE), locked_until)
                WHERE id = ?
                """, MAX_FAILED_LOGIN_COUNT, lockedUntil, lockedUntil, accountId);
    }

    public Map<String, Object> register(Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String username = values.value(request, "username", "");
        String email = values.value(request, "email", "");
        String password = values.value(request, "password", "");
        String code = values.value(request, "code", "");
        if (jdbc == null) {
            return tokenResponse(defaultUser(DEFAULT_ACCOUNT_ID), "MEMBER");
        }
        if (username.isBlank() || email.isBlank() || !email.contains("@") || password.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "username, email and password are required");
        }
        if (code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "verification code is required");
        }
        ensureRegisterAccountAvailable(jdbc, username, email);
        emailCodeService.verifyRegisterCode(email, code);
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

    public Map<String, Object> requestRegisterCode(Map<String, Object> request) {
        String email = values.value(request, "email", "");
        return emailCodeService.requestRegisterCode(email);
    }

    public Map<String, Object> requestResetPasswordCode(Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        String account = values.firstNonBlank(values.value(request, "account", ""), values.value(request, "email", ""));
        if (jdbc == null) {
            return emailCodeService.requestResetPasswordCode(null, account);
        }
        try {
            Map<String, Object> row = jdbc.queryForObject("""
                    SELECT id, email
                    FROM user_account
                    WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                    """, (rs, rowNum) -> values.map("id", rs.getLong("id"), "email", rs.getString("email")), account, account);
            return emailCodeService.requestResetPasswordCode(values.number(row.get("id"), 0L), String.valueOf(row.get("email")));
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
                emailCodeService.verifyResetPasswordCode(values.number(row.get("id"), 0L), String.valueOf(row.get("email")), code);
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
            Map<String, Object> profile = jdbc.queryForObject("""
                    SELECT ua.id, ua.username, ua.email, ua.role, ua.password_changed_time,
                           mp.id AS member_id, mp.nickname, mp.avatar_url, mp.bio,
                           ml.level_code, ml.level_name, COALESCE(ml.reward_limit, 100) AS reward_limit,
                           COALESCE(ml.min_points, 0) AS level_min_points, ml.max_points AS level_max_points,
                           COALESCE(ml.daily_download_limit, 0) AS daily_download_limit,
                           COALESCE(ml.daily_resource_publish_limit, 0) AS daily_resource_publish_limit,
                           COALESCE(ml.daily_request_publish_limit, 0) AS daily_request_publish_limit,
                           COALESCE(ml.max_files_per_resource, 0) AS max_files_per_resource,
                           COALESCE(ml.max_file_size_mb, 0) AS max_file_size_mb,
                           COALESCE(ml.can_apply_top, 0) AS can_apply_top,
                           mpa.current_points, mpa.frozen_points,
                           (SELECT next_ml.level_name
                            FROM membership_level next_ml
                            WHERE next_ml.status = 'ENABLED'
                              AND next_ml.deleted_at IS NULL
                              AND next_ml.min_points > COALESCE(mpa.current_points, 0)
                            ORDER BY next_ml.min_points ASC, next_ml.id ASC
                            LIMIT 1) AS next_level_name,
                           (SELECT next_ml.min_points
                            FROM membership_level next_ml
                            WHERE next_ml.status = 'ENABLED'
                              AND next_ml.deleted_at IS NULL
                              AND next_ml.min_points > COALESCE(mpa.current_points, 0)
                            ORDER BY next_ml.min_points ASC, next_ml.id ASC
                            LIMIT 1) AS next_level_min_points
                    FROM user_account ua
                    LEFT JOIN member_profile mp ON mp.account_id = ua.id AND mp.deleted_at IS NULL
                    LEFT JOIN member_point_account mpa ON mpa.member_id = mp.id AND mpa.deleted_at IS NULL
                    LEFT JOIN membership_level ml ON ml.id = mpa.level_id
                    WHERE ua.id = ?
                    """, mappings.userMapper(), accountId);
            appendPointRules(profile);
            return profile;
        } catch (DataAccessException ignored) {
            Map<String, Object> profile = defaultUser(accountId);
            appendPointRules(profile);
            return profile;
        }
    }

    private void appendPointRules(Map<String, Object> profile) {
        if (profile == null) {
            return;
        }
        List<Map<String, Object>> rules = pointRules.rules();
        profile.put("pointRules", rules);
        profile.put("rules", rules);
        profile.putIfAbsent("progressPercent", profile.getOrDefault("upgradeProgress", 0));
        profile.putIfAbsent("levelInfo", values.map(
                "code", profile.getOrDefault("levelCode", "NORMAL"),
                "name", profile.getOrDefault("level", "Member"),
                "minPoints", profile.getOrDefault("levelMinPoints", 0),
                "maxPoints", profile.get("levelMaxPoints")
        ));
    }

    private Map<String, Object> rewardDailyLogin(JdbcTemplate jdbc, Long accountId, Map<String, Object> profile) {
        Long memberId = values.number(profile.get("memberId"), 0L);
        if (memberId == null || memberId == 0) {
            return profile;
        }
        jdbc.update("""
                INSERT INTO member_daily_stat(stat_date, member_id, login_count)
                VALUES (CURRENT_DATE(), ?, 1)
                ON DUPLICATE KEY UPDATE login_count = login_count + 1
                """, memberId);
        boolean earned = pointManager.earn(
                memberId,
                pointRules.dailyLoginPoints(),
                "DAILY_LOGIN",
                "USER_ACCOUNT",
                accountId,
                accountId,
                "Daily login reward",
                "daily-login:" + memberId + ":" + LocalDate.now()
        );
        return earned ? userProfile(jdbc, accountId) : profile;
    }

    private void recordLogin(JdbcTemplate jdbc, Long accountId, String loginAccount, String result, String failReason) {
        jdbc.update("""
                INSERT INTO login_record(account_id, login_account, result, fail_reason)
                VALUES (?, ?, ?, ?)
                """, accountId, loginAccount, result, failReason);
    }

    private void ensureRegisterAccountAvailable(JdbcTemplate jdbc, String username, String email) {
        try {
            Integer exists = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM user_account
                    WHERE (username = ? OR email = ?) AND deleted_at IS NULL
                    """, Integer.class, username, email);
            if (exists != null && exists > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "username or email is already used");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException ignored) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "failed to check account availability");
        }
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
                "availablePoints", 650,
                "rewardLimit", 100,
                "expNeeded", 350,
                "passwordUpdatedAt", values.today()
        );
    }

    private String roleForToken(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || "AUDITOR".equals(role) ? "ADMIN" : "MEMBER";
    }
}

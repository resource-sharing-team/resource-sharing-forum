package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service("designSpecMemberService")
public class MemberService {
    private static final long DEFAULT_ACCOUNT_ID = 1L;
    private static final long DEFAULT_MEMBER_ID = 1L;

    private final TxSupport txSupport;
    private final ValueSupport values;
    private final MappingSupport mappings;
    private final PasswordEncoder passwordEncoder;

    public MemberService(
            TxSupport txSupport,
            ValueSupport values,
            MappingSupport mappings,
            PasswordEncoder passwordEncoder
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> userProfile(Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null || accountId == null) {
            return defaultUser(accountId);
        }
        try {
            return jdbc.queryForObject("""
                    SELECT ua.id, ua.username, ua.email, ua.role, ua.password_changed_time,
                           mp.id AS member_id, mp.nickname, mp.avatar_url, mp.bio,
                           ml.level_name, COALESCE(ml.reward_limit, 100) AS reward_limit,
                           mpa.current_points, mpa.frozen_points
                    FROM user_account ua
                    LEFT JOIN member_profile mp ON mp.account_id = ua.id AND mp.deleted_at IS NULL
                    LEFT JOIN member_point_account mpa ON mpa.member_id = mp.id AND mpa.deleted_at IS NULL
                    LEFT JOIN membership_level ml ON ml.id = mpa.level_id
                    WHERE ua.id = ? AND ua.deleted_at IS NULL
                    """, mappings.userMapper(), accountId);
        } catch (DataAccessException ignored) {
            return defaultUser(accountId);
        }
    }

    public Map<String, Object> updateUserProfile(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return defaultUser(accountId);
        }
        jdbc.update("""
                UPDATE member_profile
                SET nickname = COALESCE(?, nickname),
                    bio = COALESCE(?, bio),
                    avatar_url = COALESCE(?, avatar_url)
                WHERE account_id = ? AND deleted_at IS NULL
                """, values.nullable(request, "nickname"), values.nullable(request, "bio"), values.nullable(request, "avatar"), accountId);
        return userProfile(accountId);
    }

    public Map<String, Object> changePassword(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("ok", true);
        }
        String oldPassword = values.firstNonBlank(values.value(request, "oldPassword", ""), values.value(request, "currentPassword", ""));
        String newPassword = values.firstNonBlank(values.value(request, "newPassword", ""), values.value(request, "password", ""));
        if (oldPassword.isBlank() || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "old password is required and new password length must be at least 6");
        }
        Map<String, Object> account = jdbc.queryForObject("""
                SELECT password_hash
                FROM user_account
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> values.map("passwordHash", rs.getString("password_hash")), accountId);
        if (account == null || !passwordEncoder.matches(oldPassword, String.valueOf(account.get("passwordHash")))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "old password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, String.valueOf(account.get("passwordHash")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "new password cannot be the same as old password");
        }
        jdbc.update("""
                UPDATE user_account
                SET password_hash = ?, password_changed_time = NOW(3)
                WHERE id = ?
                """, passwordEncoder.encode(newPassword), accountId);
        return values.map("ok", true, "passwordUpdatedAt", values.today());
    }

    public Map<String, Object> changeEmail(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            Map<String, Object> user = defaultUser(accountId);
            user.put("email", values.firstNonBlank(values.value(request, "newEmail", ""), values.value(request, "email", ""), String.valueOf(user.get("email"))));
            return user;
        }
        String oldEmail = values.value(request, "oldEmail", "");
        String newEmail = values.firstNonBlank(values.value(request, "newEmail", ""), values.value(request, "email", ""));
        if (newEmail.isBlank() || !newEmail.contains("@")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "email format is invalid");
        }
        Map<String, Object> account = jdbc.queryForObject("""
                SELECT email
                FROM user_account
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, rowNum) -> values.map("email", rs.getString("email")), accountId);
        if (account == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "account does not exist");
        }
        String currentEmail = String.valueOf(account.get("email"));
        if (!oldEmail.isBlank() && !Objects.equals(oldEmail, currentEmail)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "old email does not match");
        }
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM user_account
                WHERE email = ? AND id <> ? AND deleted_at IS NULL
                """, Integer.class, newEmail, accountId);
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "email is already used");
        }
        jdbc.update("""
                UPDATE user_account
                SET email = ?
                WHERE id = ?
                """, newEmail, accountId);
        return userProfile(accountId);
    }

    public PageResult<Map<String, Object>> pointFlows(Long accountId, int page, int size) {
        JdbcTemplate jdbc = txSupport.jdbc();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        if (jdbc == null) {
            return new PageResult<>(0, List.of(), safePage, safeSize);
        }
        if (accountId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "please log in before viewing point flows");
        }
        Long memberId = jdbc.queryForObject("""
                SELECT mp.id
                FROM member_profile mp
                JOIN user_account ua ON ua.id = mp.account_id
                WHERE mp.account_id = ? AND mp.deleted_at IS NULL
                  AND ua.status = 'NORMAL' AND ua.deleted_at IS NULL
                """, Long.class, accountId);
        if (memberId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "member profile does not exist");
        }
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM point_flow
                WHERE member_id = ? AND deleted_at IS NULL
                """, Long.class, memberId);
        List<Map<String, Object>> list = jdbc.query("""
                SELECT id, flow_type, scene, points_change, frozen_change, before_points, after_points,
                       before_frozen_points, after_frozen_points, related_type, related_id, description, created_at
                FROM point_flow
                WHERE member_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT ?, ?
                """, (rs, rowNum) -> values.map(
                "id", rs.getLong("id"),
                "flowType", rs.getString("flow_type"),
                "scene", rs.getString("scene"),
                "pointsChange", rs.getInt("points_change"),
                "frozenChange", rs.getInt("frozen_change"),
                "beforePoints", rs.getInt("before_points"),
                "afterPoints", rs.getInt("after_points"),
                "beforeFrozenPoints", rs.getInt("before_frozen_points"),
                "afterFrozenPoints", rs.getInt("after_frozen_points"),
                "relatedType", rs.getString("related_type") == null ? "" : rs.getString("related_type"),
                "relatedId", rs.getObject("related_id") == null ? 0L : rs.getLong("related_id"),
                "description", rs.getString("description") == null ? "" : rs.getString("description"),
                "createTime", String.valueOf(rs.getObject("created_at", LocalDateTime.class))
        ), memberId, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total == null ? 0 : total, list, safePage, safeSize);
    }

    public Map<String, Object> pointAccount(Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null || accountId == null) {
            Map<String, Object> user = defaultUser(accountId);
            return values.map(
                    "points", user.get("points"),
                    "frozenPoints", user.get("frozenPoints"),
                    "availablePoints", user.get("availablePoints"),
                    "level", user.get("level"),
                    "rewardLimit", user.get("rewardLimit")
            );
        }
        try {
            return jdbc.queryForObject("""
                    SELECT mpa.current_points, mpa.frozen_points, ml.level_name, COALESCE(ml.reward_limit, 100) AS reward_limit
                    FROM member_profile mp
                    LEFT JOIN member_point_account mpa ON mpa.member_id = mp.id AND mpa.deleted_at IS NULL
                    LEFT JOIN membership_level ml ON ml.id = mpa.level_id
                    WHERE mp.account_id = ? AND mp.deleted_at IS NULL
                    """, (rs, rowNum) -> {
                int points = rs.getInt("current_points");
                int frozen = rs.getInt("frozen_points");
                return values.map(
                        "points", points,
                        "frozenPoints", frozen,
                        "availablePoints", Math.max(0, points - frozen),
                        "level", rs.getString("level_name") == null ? "Member" : rs.getString("level_name"),
                        "rewardLimit", rs.getInt("reward_limit")
                );
            }, accountId);
        } catch (DataAccessException ignored) {
            Map<String, Object> user = defaultUser(accountId);
            return values.map(
                    "points", user.get("points"),
                    "frozenPoints", user.get("frozenPoints"),
                    "availablePoints", user.get("availablePoints"),
                    "level", user.get("level"),
                    "rewardLimit", user.get("rewardLimit")
            );
        }
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
}

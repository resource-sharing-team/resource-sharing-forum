package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.point.PointRuleService;
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
    private final PointRuleService pointRules;

    public MemberService(
            TxSupport txSupport,
            ValueSupport values,
            MappingSupport mappings,
            PasswordEncoder passwordEncoder,
            PointRuleService pointRules
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.mappings = mappings;
        this.passwordEncoder = passwordEncoder;
        this.pointRules = pointRules;
    }

    public Map<String, Object> userProfile(Long accountId) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null || accountId == null) {
            return defaultUser(accountId);
        }
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
                    WHERE ua.id = ? AND ua.deleted_at IS NULL
                    """, mappings.userMapper(), accountId);
            appendPointRules(profile);
            return profile;
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
                "createTime", String.valueOf(rs.getObject("created_at", LocalDateTime.class)),
                "sceneLabel", sceneLabel(rs.getString("scene"), rs.getString("flow_type")),
                "relatedLabel", relatedLabel(rs.getString("related_type"), rs.getObject("related_id")),
                "balanceText", balanceText(rs.getInt("after_points"), rs.getInt("after_frozen_points"))
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
                    "rewardLimit", user.get("rewardLimit"),
                    "levelInfo", user.get("levelInfo"),
                    "nextLevel", user.get("nextLevel"),
                    "progressPercent", user.get("progressPercent"),
                    "benefits", user.get("benefits"),
                    "pointRules", user.get("pointRules"),
                    "rules", user.get("rules")
            );
        }
        try {
            return jdbc.queryForObject("""
                    SELECT mpa.current_points, mpa.frozen_points,
                           ml.level_code, ml.level_name, COALESCE(ml.reward_limit, 100) AS reward_limit,
                           COALESCE(ml.min_points, 0) AS level_min_points, ml.max_points AS level_max_points,
                           COALESCE(ml.daily_download_limit, 0) AS daily_download_limit,
                           COALESCE(ml.daily_resource_publish_limit, 0) AS daily_resource_publish_limit,
                           COALESCE(ml.daily_request_publish_limit, 0) AS daily_request_publish_limit,
                           COALESCE(ml.max_files_per_resource, 0) AS max_files_per_resource,
                           COALESCE(ml.max_file_size_mb, 0) AS max_file_size_mb,
                           COALESCE(ml.can_apply_top, 0) AS can_apply_top,
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
                    FROM member_profile mp
                    LEFT JOIN member_point_account mpa ON mpa.member_id = mp.id AND mpa.deleted_at IS NULL
                    LEFT JOIN membership_level ml ON ml.id = mpa.level_id
                    WHERE mp.account_id = ? AND mp.deleted_at IS NULL
                    """, (rs, rowNum) -> {
                int points = rs.getInt("current_points");
                int frozen = rs.getInt("frozen_points");
                int levelMin = rs.getInt("level_min_points");
                Object nextLevelMinObject = rs.getObject("next_level_min_points");
                Integer nextLevelMin = nextLevelMinObject == null ? null : ((Number) nextLevelMinObject).intValue();
                int expNeeded = nextLevelMin == null ? 0 : Math.max(0, nextLevelMin - points);
                int progress = nextLevelMin == null ? 100 : Math.max(0, Math.min(100,
                        (int) Math.round((points - levelMin) * 100.0 / Math.max(1, nextLevelMin - levelMin))));
                String levelCode = values.firstNonBlank(rs.getString("level_code"), "NORMAL");
                String levelName = rs.getString("level_name") == null ? "Member" : rs.getString("level_name");
                List<Map<String, Object>> benefits = benefits(
                        rs.getInt("daily_download_limit"),
                        rs.getInt("daily_resource_publish_limit"),
                        rs.getInt("daily_request_publish_limit"),
                        rs.getInt("max_files_per_resource"),
                        rs.getInt("max_file_size_mb"),
                        rs.getInt("reward_limit"),
                        rs.getInt("can_apply_top") == 1
                );
                List<Map<String, Object>> rules = pointRules.rules();
                return values.map(
                        "points", points,
                        "frozenPoints", frozen,
                        "availablePoints", Math.max(0, points - frozen),
                        "levelCode", levelCode,
                        "level", levelName,
                        "levelMinPoints", levelMin,
                        "levelMaxPoints", rs.getObject("level_max_points"),
                        "levelInfo", values.map(
                                "code", levelCode,
                                "name", levelName,
                                "minPoints", levelMin,
                                "maxPoints", rs.getObject("level_max_points")
                        ),
                        "nextLevel", values.firstNonBlank(rs.getString("next_level_name"), ""),
                        "nextLevelMinPoints", nextLevelMin == null ? 0 : nextLevelMin,
                        "rewardLimit", rs.getInt("reward_limit"),
                        "dailyDownloadLimit", rs.getInt("daily_download_limit"),
                        "dailyResourcePublishLimit", rs.getInt("daily_resource_publish_limit"),
                        "dailyRequestPublishLimit", rs.getInt("daily_request_publish_limit"),
                        "maxFilesPerResource", rs.getInt("max_files_per_resource"),
                        "maxFileSizeMb", rs.getInt("max_file_size_mb"),
                        "canApplyTop", rs.getInt("can_apply_top") == 1,
                        "expNeeded", expNeeded,
                        "upgradeProgress", progress,
                        "progressPercent", progress,
                        "benefits", benefits,
                        "pointRules", rules,
                        "rules", rules
                );
            }, accountId);
        } catch (DataAccessException ignored) {
            Map<String, Object> user = defaultUser(accountId);
            return values.map(
                    "points", user.get("points"),
                    "frozenPoints", user.get("frozenPoints"),
                    "availablePoints", user.get("availablePoints"),
                    "level", user.get("level"),
                    "rewardLimit", user.get("rewardLimit"),
                    "levelInfo", user.get("levelInfo"),
                    "nextLevel", user.get("nextLevel"),
                    "progressPercent", user.get("progressPercent"),
                    "benefits", user.get("benefits"),
                    "pointRules", user.get("pointRules"),
                    "rules", user.get("rules")
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
                "levelInfo", values.map("code", "NORMAL", "name", "Member", "minPoints", 0, "maxPoints", null),
                "nextLevel", "",
                "points", 650,
                "frozenPoints", 0,
                "availablePoints", 650,
                "rewardLimit", 100,
                "expNeeded", 350,
                "upgradeProgress", 65,
                "progressPercent", 65,
                "benefits", benefits(10, 5, 5, 5, 100, 100, false),
                "pointRules", pointRules.rules(),
                "rules", pointRules.rules(),
                "passwordUpdatedAt", values.today()
        );
    }

    private void appendPointRules(Map<String, Object> profile) {
        if (profile != null) {
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
    }

    private List<Map<String, Object>> benefits(
            int dailyDownloadLimit,
            int dailyResourcePublishLimit,
            int dailyRequestPublishLimit,
            int maxFilesPerResource,
            int maxFileSizeMb,
            int rewardLimit,
            boolean canApplyTop
    ) {
        return List.of(
                values.map("name", "每日下载次数", "description", "每天可成功下载的资源附件次数", "limit", dailyDownloadLimit, "enabled", dailyDownloadLimit > 0),
                values.map("name", "每日发布资源", "description", "每天可发布的资源数量", "limit", dailyResourcePublishLimit, "enabled", dailyResourcePublishLimit > 0),
                values.map("name", "每日发布求资源", "description", "每天可发布的求资源帖子数量", "limit", dailyRequestPublishLimit, "enabled", dailyRequestPublishLimit > 0),
                values.map("name", "单资源附件数", "description", "单个资源最多可上传的附件数量", "limit", maxFilesPerResource, "enabled", maxFilesPerResource > 0),
                values.map("name", "单附件大小", "description", "单个附件最大体积，单位 MB", "limit", maxFileSizeMb, "enabled", maxFileSizeMb > 0),
                values.map("name", "单帖悬赏上限", "description", "单个求资源帖子可设置的最高悬赏积分", "limit", rewardLimit, "enabled", rewardLimit > 0),
                values.map("name", "申请置顶", "description", "当前等级是否允许申请资源置顶", "limit", canApplyTop ? "允许" : "不允许", "enabled", canApplyTop)
        );
    }

    private String sceneLabel(String scene, String flowType) {
        String normalized = values.firstNonBlank(scene, flowType);
        return switch (normalized) {
            case "DAILY_LOGIN" -> "每日登录";
            case "RESOURCE_FAVORITE", "RESOURCE_FAVORITED" -> "资源被收藏";
            case "RESOURCE_LIKE", "RESOURCE_LIKED" -> "资源被点赞";
            case "RESOURCE_APPROVED" -> "资源审核通过";
            case "RESOURCE_DOWNLOAD", "RESOURCE_DOWNLOADED" -> "资源被下载";
            case "REQUEST_REWARD" -> "求资源悬赏冻结";
            case "REQUEST_SETTLE" -> "求资源悬赏结算";
            case "REQUEST_REWARD_COLLECT" -> "悬赏违规收回";
            case "REQUEST_REWARD_RESTORE" -> "申诉恢复悬赏";
            case "REQUEST_ACCEPTED", "REQUEST_ACCEPTED_BONUS" -> "回答被采纳";
            case "VIOLATION_PENALTY", "VIOLATION_CONFIRMED" -> "违规扣分";
            case "FREEZE" -> "冻结积分";
            case "UNFREEZE" -> "解冻积分";
            case "TRANSFER_IN" -> "悬赏收入";
            case "TRANSFER_OUT" -> "悬赏支出";
            case "EARN" -> "积分收入";
            case "DEDUCT" -> "积分扣减";
            default -> normalized;
        };
    }

    private String relatedLabel(String relatedType, Object relatedId) {
        String type = values.firstNonBlank(relatedType, "MANUAL");
        String prefix = switch (type) {
            case "RESOURCE" -> "资源";
            case "REQUEST_POST" -> "求资源";
            case "REQUEST_REPLY" -> "回答";
            case "COMMENT" -> "评论";
            case "REPORT" -> "举报";
            case "APPEAL" -> "申诉";
            default -> "关联对象";
        };
        return relatedId == null ? prefix : prefix + " #" + relatedId;
    }

    private String balanceText(int points, int frozenPoints) {
        return "当前 " + points + " 分，冻结 " + frozenPoints + " 分";
    }
}

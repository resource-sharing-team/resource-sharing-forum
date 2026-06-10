package com.resourcesharing.forum.service.point;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JdbcPointManager implements PointManager {
    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    public JdbcPointManager(ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbcProvider = jdbcProvider;
    }

    @Override
    @Transactional
    public void freezeForRequest(Long memberId, Integer points, Long requestId) {
        int reward = normalizePoints(points);
        if (reward == 0) {
            return;
        }
        ensureRequestId(requestId);
        RequestPointSnapshot request = requestForUpdate(requestId);
        if (!request.requesterId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the request owner can freeze reward points");
        }
        ensureNoFlow(requestId, "FREEZE");

        PointAccount before = pointAccountForUpdate(memberId);
        if (before.availablePoints() < reward) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Insufficient points");
        }

        jdbc().update("""
                UPDATE member_point_account
                SET frozen_points = frozen_points + ?
                WHERE member_id = ?
                """, reward, memberId);
        updateRequestRewardStatus(requestId, "FROZEN");
        insertFlow(memberId, "FREEZE", "REQUEST_REWARD", 0, reward,
                before.currentPoints(), before.currentPoints(), before.frozenPoints(),
                before.frozenPoints() + reward, requestId, null,
                "Freeze request reward points", "request:freeze:" + requestId);
    }

    @Override
    @Transactional
    public void refundRequest(Long requestId) {
        RequestPointSnapshot request = requestForUpdate(requestId);
        if (request.rewardPoints() == 0) {
            return;
        }
        ensureNoFlow(requestId, "UNFREEZE");
        ensureNoFlow(requestId, "TRANSFER_OUT");
        ensureNoFlow(requestId, "DEDUCT");

        PointAccount before = pointAccountForUpdate(request.requesterId());
        if (before.frozenPoints() < request.rewardPoints()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Frozen points are insufficient for refund");
        }

        int afterFrozen = before.frozenPoints() - request.rewardPoints();
        jdbc().update("""
                UPDATE member_point_account
                SET frozen_points = ?
                WHERE member_id = ?
                """, afterFrozen, request.requesterId());
        updateRequestRewardStatus(requestId, "RETURNED");
        insertFlow(request.requesterId(), "UNFREEZE", "REQUEST_REWARD", 0, -request.rewardPoints(),
                before.currentPoints(), before.currentPoints(), before.frozenPoints(),
                afterFrozen, requestId, null,
                "Refund frozen request reward points", "request:refund:" + requestId);
    }

    @Override
    @Transactional
    public void transferReward(Long requestId, Long winnerMemberId) {
        RequestPointSnapshot request = requestForUpdate(requestId);
        if (request.rewardPoints() == 0) {
            return;
        }
        if (request.requesterId().equals(winnerMemberId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request reward cannot be transferred to the requester");
        }
        ensureNoFlow(requestId, "TRANSFER_OUT");
        ensureNoFlow(requestId, "UNFREEZE");
        ensureNoFlow(requestId, "DEDUCT");

        Map<Long, PointAccount> locked = pointAccountsForUpdateInOrder(request.requesterId(), winnerMemberId);
        PointAccount requester = locked.get(request.requesterId());
        PointAccount winner = locked.get(winnerMemberId);
        if (requester == null || winner == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Point account not found");
        }
        if (requester.frozenPoints() < request.rewardPoints() || requester.currentPoints() < request.rewardPoints()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request reward point account is inconsistent");
        }

        jdbc().update("""
                UPDATE member_point_account
                SET current_points = current_points - ?,
                    frozen_points = frozen_points - ?,
                    total_spent_points = total_spent_points + ?
                WHERE member_id = ?
                """, request.rewardPoints(), request.rewardPoints(), request.rewardPoints(), request.requesterId());
        jdbc().update("""
                UPDATE member_point_account
                SET current_points = current_points + ?,
                    total_earned_points = total_earned_points + ?
                WHERE member_id = ?
                """, request.rewardPoints(), request.rewardPoints(), winnerMemberId);

        insertFlow(request.requesterId(), "TRANSFER_OUT", "REQUEST_SETTLE", -request.rewardPoints(), -request.rewardPoints(),
                requester.currentPoints(), requester.currentPoints() - request.rewardPoints(),
                requester.frozenPoints(), requester.frozenPoints() - request.rewardPoints(),
                requestId, null, "Transfer request reward out", "request:transfer-out:" + requestId);
        insertFlow(winnerMemberId, "TRANSFER_IN", "REQUEST_SETTLE", request.rewardPoints(), 0,
                winner.currentPoints(), winner.currentPoints() + request.rewardPoints(),
                winner.frozenPoints(), winner.frozenPoints(), requestId, null,
                "Transfer request reward in", "request:transfer-in:" + requestId);
        updateRequestRewardStatus(requestId, "PAID");
        recalculateLevel(request.requesterId());
        recalculateLevel(winnerMemberId);
    }

    @Override
    @Transactional
    public void collectFrozenRequest(Long requestId, Long operatorId, String description) {
        RequestPointSnapshot request = requestForUpdate(requestId);
        if (request.rewardPoints() == 0) {
            return;
        }
        ensureNoFlow(requestId, "DEDUCT");
        ensureNoFlow(requestId, "UNFREEZE");
        ensureNoFlow(requestId, "TRANSFER_OUT");

        PointAccount before = pointAccountForUpdate(request.requesterId());
        if (before.frozenPoints() < request.rewardPoints() || before.currentPoints() < request.rewardPoints()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Frozen reward points are insufficient for collection");
        }

        jdbc().update("""
                UPDATE member_point_account
                SET current_points = current_points - ?,
                    frozen_points = frozen_points - ?,
                    total_spent_points = total_spent_points + ?
                WHERE member_id = ?
                """, request.rewardPoints(), request.rewardPoints(), request.rewardPoints(), request.requesterId());
        updateRequestRewardStatus(requestId, "COLLECTED");
        insertFlow(request.requesterId(), "DEDUCT", "REQUEST_REWARD_COLLECT", -request.rewardPoints(), -request.rewardPoints(),
                before.currentPoints(), before.currentPoints() - request.rewardPoints(),
                before.frozenPoints(), before.frozenPoints() - request.rewardPoints(),
                requestId, operatorId, firstNonBlank(description, "Collect frozen request reward points"),
                "request:collect:" + requestId);
        recalculateLevel(request.requesterId());
    }

    @Override
    @Transactional
    public void restoreCollectedRequest(Long requestId, Long operatorId, String description) {
        RequestPointSnapshot request = requestForUpdate(requestId);
        if (request.rewardPoints() == 0) {
            return;
        }
        ensureNoFlow(requestId, "REFUND");
        PointAccount before = pointAccountForUpdate(request.requesterId());
        jdbc().update("""
                UPDATE member_point_account
                SET current_points = current_points + ?,
                    frozen_points = frozen_points + ?,
                    total_earned_points = total_earned_points + ?
                WHERE member_id = ?
                """, request.rewardPoints(), request.rewardPoints(), request.rewardPoints(), request.requesterId());
        updateRequestRewardStatus(requestId, "FROZEN");
        insertFlow(request.requesterId(), "REFUND", "REQUEST_REWARD_RESTORE", request.rewardPoints(), request.rewardPoints(),
                before.currentPoints(), before.currentPoints() + request.rewardPoints(),
                before.frozenPoints(), before.frozenPoints() + request.rewardPoints(),
                requestId, operatorId, firstNonBlank(description, "Restore collected request reward points"),
                "request:restore:" + requestId);
        recalculateLevel(request.requesterId());
    }

    @Override
    @Transactional
    public boolean earn(
            Long memberId,
            Integer points,
            String scene,
            String relatedType,
            Long relatedId,
            Long operatorId,
            String description,
            String bizKey
    ) {
        int amount = normalizePoints(points);
        if (amount == 0 || memberId == null || memberId <= 0 || bizKeyExists(bizKey)) {
            return false;
        }
        PointAccount before = pointAccountForUpdate(memberId);
        int after = before.currentPoints() + amount;
        jdbc().update("""
                UPDATE member_point_account
                SET current_points = ?,
                    total_earned_points = total_earned_points + ?
                WHERE member_id = ?
                """, after, amount, memberId);
        insertFlowGeneric(memberId, "EARN", firstNonBlank(scene, "POINT_EARN"), amount, 0,
                before.currentPoints(), after, before.frozenPoints(), before.frozenPoints(),
                relatedType, relatedId, operatorId, description, bizKey);
        recalculateLevel(memberId);
        return true;
    }

    @Override
    @Transactional
    public boolean deduct(
            Long memberId,
            Integer points,
            String scene,
            String relatedType,
            Long relatedId,
            Long operatorId,
            String description,
            String bizKey
    ) {
        int amount = normalizePoints(points);
        if (amount == 0 || memberId == null || memberId <= 0 || bizKeyExists(bizKey)) {
            return false;
        }
        PointAccount before = pointAccountForUpdate(memberId);
        int maxDeductible = Math.max(0, before.currentPoints() - before.frozenPoints());
        int actual = Math.min(amount, maxDeductible);
        if (actual == 0) {
            return false;
        }
        int after = before.currentPoints() - actual;
        jdbc().update("""
                UPDATE member_point_account
                SET current_points = ?,
                    total_spent_points = total_spent_points + ?
                WHERE member_id = ?
                """, after, actual, memberId);
        insertFlowGeneric(memberId, "DEDUCT", firstNonBlank(scene, "POINT_DEDUCT"), -actual, 0,
                before.currentPoints(), after, before.frozenPoints(), before.frozenPoints(),
                relatedType, relatedId, operatorId, description, bizKey);
        recalculateLevel(memberId);
        return true;
    }

    @Override
    @Transactional
    public Long recordEvent(Long memberId, String type, Integer amount, Integer afterBalance, String scene, Long relatedTargetId, String remark) {
        int change = amount == null ? 0 : amount;
        int after = afterBalance == null ? 0 : afterBalance;
        return insertFlowGeneric(memberId, type, scene, change, 0,
                after - change, after, 0, 0, "MANUAL", relatedTargetId, null, remark, null);
    }

    private RequestPointSnapshot requestForUpdate(Long requestId) {
        ensureRequestId(requestId);
        return jdbc().queryForObject("""
                SELECT requester_id, reward_points, COALESCE(reward_status, IF(reward_points > 0, 'PENDING', 'NONE')) AS reward_status
                FROM request_post
                WHERE id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new RequestPointSnapshot(
                rs.getLong("requester_id"),
                rs.getInt("reward_points"),
                rs.getString("reward_status")
        ), requestId);
    }

    private PointAccount pointAccountForUpdate(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Member id is required for point operation");
        }
        return jdbc().queryForObject("""
                SELECT member_id, current_points, frozen_points
                FROM member_point_account
                WHERE member_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new PointAccount(
                rs.getLong("member_id"),
                rs.getInt("current_points"),
                rs.getInt("frozen_points")
        ), memberId);
    }

    private Map<Long, PointAccount> pointAccountsForUpdateInOrder(Long firstMemberId, Long secondMemberId) {
        List<Long> ids = List.of(firstMemberId, secondMemberId).stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        Map<Long, PointAccount> result = new LinkedHashMap<>();
        for (Long id : ids) {
            PointAccount account = pointAccountForUpdate(id);
            result.put(id, account);
        }
        return result;
    }

    private void ensureNoFlow(Long requestId, String flowType) {
        Integer count = jdbc().queryForObject("""
                SELECT COUNT(*)
                FROM point_flow
                WHERE related_type = 'REQUEST_POST' AND related_id = ? AND flow_type = ?
                  AND deleted_at IS NULL
                """, Integer.class, requestId, flowType);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Reward point operation has already been processed");
        }
    }

    private void updateRequestRewardStatus(Long requestId, String status) {
        jdbc().update("UPDATE request_post SET reward_status = ? WHERE id = ?", status, requestId);
    }

    private void insertFlow(
            Long memberId,
            String flowType,
            String scene,
            int pointsChange,
            int frozenChange,
            int beforePoints,
            int afterPoints,
            int beforeFrozen,
            int afterFrozen,
            Long requestId,
            Long operatorId,
            String description,
            String bizKey
    ) {
        insertFlowGeneric(memberId, flowType, scene, pointsChange, frozenChange, beforePoints, afterPoints,
                beforeFrozen, afterFrozen, "REQUEST_POST", requestId, operatorId, description, bizKey);
    }

    private Long insertFlowGeneric(
            Long memberId,
            String flowType,
            String scene,
            int pointsChange,
            int frozenChange,
            int beforePoints,
            int afterPoints,
            int beforeFrozen,
            int afterFrozen,
            String relatedType,
            Long relatedId,
            Long operatorId,
            String description,
            String bizKey
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc().update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO point_flow(member_id, flow_type, scene, points_change, frozen_change, before_points, after_points,
                                           before_frozen_points, after_frozen_points, related_type, related_id, operator_id, description, biz_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            statement.setString(2, firstNonBlank(flowType, "ADJUST"));
            statement.setString(3, firstNonBlank(scene, "POINT_EVENT"));
            statement.setInt(4, pointsChange);
            statement.setInt(5, frozenChange);
            statement.setInt(6, beforePoints);
            statement.setInt(7, afterPoints);
            statement.setInt(8, beforeFrozen);
            statement.setInt(9, afterFrozen);
            statement.setString(10, firstNonBlank(relatedType, "MANUAL"));
            if (relatedId == null || relatedId == 0) {
                statement.setObject(11, null);
            } else {
                statement.setLong(11, relatedId);
            }
            if (operatorId == null || operatorId == 0) {
                statement.setObject(12, null);
            } else {
                statement.setLong(12, operatorId);
            }
            statement.setString(13, firstNonBlank(description, firstNonBlank(scene, "Point event")));
            statement.setString(14, blankToNull(bizKey));
            return statement;
        }, keyHolder);
        updateDailyPointChange(memberId, pointsChange);
        return keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    }

    private void updateDailyPointChange(Long memberId, int pointsChange) {
        if (memberId == null || pointsChange == 0) {
            return;
        }
        jdbc().update("""
                INSERT INTO member_daily_stat(stat_date, member_id, point_change)
                VALUES (CURRENT_DATE(), ?, ?)
                ON DUPLICATE KEY UPDATE point_change = point_change + VALUES(point_change)
                """, memberId, pointsChange);
    }

    private boolean bizKeyExists(String bizKey) {
        String normalized = blankToNull(bizKey);
        if (normalized == null) {
            return false;
        }
        try {
            Integer count = jdbc().queryForObject("""
                    SELECT COUNT(*)
                    FROM point_flow
                    WHERE biz_key = ? AND deleted_at IS NULL
                    """, Integer.class, normalized);
            return count != null && count > 0;
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    private void recalculateLevel(Long memberId) {
        try {
            PointAccount account = jdbc().queryForObject("""
                    SELECT member_id, current_points, frozen_points
                    FROM member_point_account
                    WHERE member_id = ?
                    """, (rs, rowNum) -> new PointAccount(rs.getLong("member_id"), rs.getInt("current_points"), rs.getInt("frozen_points")), memberId);
            if (account == null) {
                return;
            }
            Long levelId = jdbc().queryForObject("""
                    SELECT id
                    FROM membership_level
                    WHERE status = 'ENABLED'
                      AND deleted_at IS NULL
                      AND min_points <= ?
                      AND (max_points IS NULL OR max_points >= ?)
                    ORDER BY min_points DESC, id DESC
                    LIMIT 1
                    """, Long.class, account.currentPoints(), account.currentPoints());
            if (levelId != null) {
                jdbc().update("UPDATE member_point_account SET level_id = ? WHERE member_id = ?", levelId, memberId);
            }
        } catch (DataAccessException ignored) {
            // Level recalculation is best-effort when seed levels are unavailable.
        }
    }

    private int normalizePoints(Integer points) {
        if (points == null || points < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Point amount must be non-negative");
        }
        return points;
    }

    private void ensureRequestId(Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Request id is required for point operation");
        }
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Point service requires a database connection");
        }
        return jdbc;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RequestPointSnapshot(Long requesterId, int rewardPoints, String rewardStatus) {
    }

    private record PointAccount(Long memberId, int currentPoints, int frozenPoints) {
        int availablePoints() {
            return currentPoints - frozenPoints;
        }
    }
}

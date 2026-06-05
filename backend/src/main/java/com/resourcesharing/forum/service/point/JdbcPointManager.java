package com.resourcesharing.forum.service.point;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
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
        insertFlow(memberId, "FREEZE", "REQUEST_REWARD", 0, reward,
                before.currentPoints(), before.currentPoints(), before.frozenPoints(),
                before.frozenPoints() + reward, requestId, null, "Freeze request reward points");
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
        insertFlow(request.requesterId(), "UNFREEZE", "REQUEST_REWARD", 0, -request.rewardPoints(),
                before.currentPoints(), before.currentPoints(), before.frozenPoints(),
                afterFrozen, requestId, null, "Refund frozen request reward points");
    }

    @Override
    @Transactional
    public void transferReward(Long requestId, Long winnerMemberId) {
        RequestPointSnapshot request = requestForUpdate(requestId);
        if (request.rewardPoints() == 0) {
            return;
        }
        ensureNoFlow(requestId, "TRANSFER_OUT");
        ensureNoFlow(requestId, "UNFREEZE");

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
                requestId, null, "Transfer request reward out");
        insertFlow(winnerMemberId, "TRANSFER_IN", "REQUEST_SETTLE", request.rewardPoints(), 0,
                winner.currentPoints(), winner.currentPoints() + request.rewardPoints(),
                winner.frozenPoints(), winner.frozenPoints(), requestId, null, "Transfer request reward in");
    }

    @Override
    @Transactional
    public Long recordEvent(Long memberId, String type, Integer amount, Integer afterBalance, String scene, Long relatedTargetId, String remark) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc().update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO point_flow(member_id, flow_type, scene, points_change, frozen_change,
                                           before_points, after_points, before_frozen_points, after_frozen_points,
                                           related_type, related_id, operator_id, description)
                    VALUES (?, ?, ?, ?, 0, ?, ?, 0, 0, 'MANUAL', ?, NULL, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            int change = amount == null ? 0 : amount;
            int after = afterBalance == null ? 0 : afterBalance;
            statement.setLong(1, memberId);
            statement.setString(2, type);
            statement.setString(3, scene);
            statement.setInt(4, change);
            statement.setInt(5, after - change);
            statement.setInt(6, after);
            statement.setObject(7, relatedTargetId);
            statement.setString(8, remark);
            return statement;
        }, keyHolder);
        return keyHolder.getKey() == null ? 0L : keyHolder.getKey().longValue();
    }

    private RequestPointSnapshot requestForUpdate(Long requestId) {
        ensureRequestId(requestId);
        return jdbc().queryForObject("""
                SELECT requester_id, reward_points
                FROM request_post
                WHERE id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new RequestPointSnapshot(rs.getLong("requester_id"), rs.getInt("reward_points")), requestId);
    }

    private PointAccount pointAccountForUpdate(Long memberId) {
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
            String description
    ) {
        jdbc().update("""
                INSERT INTO point_flow(member_id, flow_type, scene, points_change, frozen_change, before_points, after_points,
                                       before_frozen_points, after_frozen_points, related_type, related_id, operator_id, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'REQUEST_POST', ?, ?, ?)
                """, memberId, flowType, scene, pointsChange, frozenChange, beforePoints, afterPoints,
                beforeFrozen, afterFrozen, requestId, operatorId, description);
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

    private record RequestPointSnapshot(Long requesterId, int rewardPoints) {
    }

    private record PointAccount(Long memberId, int currentPoints, int frozenPoints) {
        int availablePoints() {
            return currentPoints - frozenPoints;
        }
    }
}

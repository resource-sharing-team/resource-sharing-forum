package com.resourcesharing.forum.service.audit;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.domain.statemachine.RequestStateMachine;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.resource.ResourceService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import com.resourcesharing.forum.service.system.AdminLogService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
public class AppealService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final AdminLogService adminLogService;
    private final NotificationDispatcher notificationDispatcher;
    private final ResourceService resourceService;
    private final PointManager pointManager;

    public AppealService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            AdminLogService adminLogService,
            NotificationDispatcher notificationDispatcher,
            ResourceService resourceService,
            PointManager pointManager
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.adminLogService = adminLogService;
        this.notificationDispatcher = notificationDispatcher;
        this.resourceService = resourceService;
        this.pointManager = pointManager;
    }

    public Map<String, Object> appeal(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("ok", true, "status", "PENDING");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO appeal_record(appellant_id, target_type, target_id, related_report_id, reason, status)
                    VALUES (?, ?, ?, ?, ?, 'PENDING')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, lookup.requireMemberId(accountId));
            statement.setString(2, appealTarget(values.value(request, "targetType", "RESOURCE")));
            statement.setLong(3, values.number(values.firstPresent(request, "targetId"), 0L));
            Long reportId = values.number(values.firstPresent(request, "relatedReportId"), 0L);
            if (reportId == 0) {
                statement.setObject(4, null);
            } else {
                statement.setLong(4, reportId);
            }
            statement.setString(5, values.firstNonBlank(values.value(request, "reason", ""), "User submitted appeal"));
            return statement;
        }, keyHolder);
        return values.map("ok", true, "id", values.key(keyHolder), "status", "PENDING");
    }

    public Map<String, Object> handleAppeal(Long appealId, Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", appealId, "status", "APPROVED");
        }
        return txSupport.required(() -> {
            Long adminId = lookup.adminProfileId(adminAccountId);
            Map<String, Object> appeal = appealForUpdate(jdbc, appealId);
            String before = String.valueOf(appeal.get("status"));
            String status = values.firstNonBlank(values.value(request, "status", ""), values.value(request, "result", ""), "APPROVED").toUpperCase();
            if (!List.of("APPROVED", "REJECTED", "PROCESSING").contains(status)) {
                status = "APPROVED";
            }
            String handleResult = values.firstNonBlank(values.value(request, "handleResult", ""), values.value(request, "reason", ""), "Admin handled appeal");
            jdbc.update("""
                    UPDATE appeal_record
                    SET status = ?, handler_id = ?, handle_result = ?, handle_time = NOW(3)
                    WHERE id = ? AND deleted_at IS NULL
                    """, status, adminId, handleResult, appealId);
            if ("APPROVED".equals(status) && "RESOURCE".equals(appeal.get("targetType"))) {
                resourceService.transitionResourceByAdmin(
                        values.number(appeal.get("targetId"), appealId),
                        adminAccountId,
                        "RESTORE",
                        Map.of("reason", handleResult)
                );
            }
            if ("APPROVED".equals(status) && "REQUEST_POST".equals(appeal.get("targetType"))) {
                restoreRequestPost(jdbc, values.number(appeal.get("targetId"), appealId), adminAccountId, handleResult);
            }
            adminLogService.record(adminId, "APPEAL_HANDLE", "APPEAL", appealId, before, status);
            notificationDispatcher.dispatchToMember(
                    values.number(appeal.get("appellantId"), 0L),
                    "APPEAL_RESULT",
                    "Appeal handled",
                    "Your appeal has been handled. Result: " + status + ". " + handleResult,
                    String.valueOf(appeal.get("targetType")),
                    values.number(appeal.get("targetId"), appealId)
            );
            return values.map("id", appealId, "status", status);
        });
    }

    private Map<String, Object> appealForUpdate(JdbcTemplate jdbc, Long appealId) {
        try {
            return jdbc.queryForObject("""
                    SELECT appellant_id, target_type, target_id, status
                    FROM appeal_record
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "appellantId", rs.getLong("appellant_id"),
                    "targetType", rs.getString("target_type"),
                    "targetId", rs.getLong("target_id"),
                    "status", rs.getString("status")
            ), appealId);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Appeal does not exist");
        }
    }

    private void restoreRequestPost(JdbcTemplate jdbc, Long requestId, Long adminAccountId, String reason) {
        Map<String, Object> post = jdbc.queryForObject("""
                SELECT status, reward_points, reward_status
                FROM request_post
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """, (rs, rowNum) -> values.map(
                "status", rs.getString("status"),
                "rewardPoints", rs.getInt("reward_points"),
                "rewardStatus", rs.getString("reward_status")
        ), requestId);
        String before = String.valueOf(post.get("status"));
        RequestStateMachine.assertCanTransit(before, "ONGOING", "RESTORE", "ADMIN", false, reason);
        if (values.number(post.get("rewardPoints"), 0L) > 0 && "COLLECTED".equals(post.get("rewardStatus"))) {
            pointManager.restoreCollectedRequest(requestId, adminAccountId, reason);
        }
        jdbc.update("UPDATE request_post SET status = 'ONGOING', closed_time = NULL WHERE id = ?", requestId);
        jdbc.update("""
                INSERT INTO request_status_log(request_id, from_status, to_status, operator_id, reason)
                VALUES (?, ?, 'ONGOING', ?, ?)
                """, requestId, before, adminAccountId, reason);
    }

    private String appealTarget(String target) {
        return switch (values.firstNonBlank(target).toUpperCase()) {
            case "DEMAND", "REQUEST", "REQUEST_POST" -> "REQUEST_POST";
            case "COMMENT" -> "COMMENT";
            case "REQUEST_REPLY" -> "REQUEST_REPLY";
            case "USER" -> "USER";
            default -> "RESOURCE";
        };
    }
}

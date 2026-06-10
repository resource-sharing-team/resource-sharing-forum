package com.resourcesharing.forum.service.audit;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.point.PointManager;
import com.resourcesharing.forum.service.point.PointRuleService;
import com.resourcesharing.forum.service.request.RequestRewardService;
import com.resourcesharing.forum.service.resource.ResourceService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import com.resourcesharing.forum.service.system.AdminLogService;
import com.resourcesharing.forum.service.system.AdminMemberService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
public class ReportComplaintService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final AdminLogService adminLogService;
    private final NotificationDispatcher notificationDispatcher;
    private final ResourceService resourceService;
    private final RequestRewardService requestRewardService;
    private final AdminMemberService adminMemberService;
    private final PointManager pointManager;
    private final PointRuleService pointRules;

    public ReportComplaintService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            AdminLogService adminLogService,
            NotificationDispatcher notificationDispatcher,
            ResourceService resourceService,
            RequestRewardService requestRewardService,
            AdminMemberService adminMemberService,
            PointManager pointManager,
            PointRuleService pointRules
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.adminLogService = adminLogService;
        this.notificationDispatcher = notificationDispatcher;
        this.resourceService = resourceService;
        this.requestRewardService = requestRewardService;
        this.adminMemberService = adminMemberService;
        this.pointManager = pointManager;
        this.pointRules = pointRules;
    }

    public Map<String, Object> report(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("ok", true, "status", "PENDING");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO report_complaint(reporter_id, report_type, target_type, target_id, title, reason, proof_summary, contact_email, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                        """, Statement.RETURN_GENERATED_KEYS);
                String targetType = reportTarget(values.value(request, "targetType", values.value(request, "target", "RESOURCE")));
                statement.setLong(1, lookup.requireMemberId(accountId));
                statement.setString(2, reportType(values.value(request, "type", "RESOURCE"), targetType));
                statement.setString(3, targetType);
                statement.setLong(4, values.number(values.firstPresent(request, "targetId"), 0L));
                statement.setString(5, values.blankToNull(values.value(request, "title", "")));
                statement.setString(6, values.firstNonBlank(values.value(request, "reason", ""), "User submitted report"));
                statement.setString(7, values.blankToNull(values.value(request, "proofSummary", "")));
                statement.setString(8, values.blankToNull(values.value(request, "contactEmail", "")));
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已提交过该举报，处理中请勿重复提交");
        }
        return values.map("ok", true, "id", values.key(keyHolder), "status", "PENDING");
    }

    public Map<String, Object> handleReport(Long reportId, Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", reportId, "status", "RESOLVED");
        }
        return txSupport.required(() -> {
            Long adminId = lookup.adminProfileId(adminAccountId);
            Map<String, Object> report = reportForUpdate(jdbc, reportId);
            String before = String.valueOf(report.get("status"));
            String status = values.firstNonBlank(values.value(request, "status", ""), values.value(request, "result", ""), "RESOLVED").toUpperCase();
            if (!List.of("RESOLVED", "REJECTED", "PROCESSING").contains(status)) {
                status = "RESOLVED";
            }
            String handleResult = values.firstNonBlank(values.value(request, "handleResult", ""), values.value(request, "reason", ""), "Admin handled report");
            jdbc.update("""
                    UPDATE report_complaint
                    SET status = ?, handler_id = ?, handle_result = ?, handle_time = NOW(3)
                    WHERE id = ? AND deleted_at IS NULL
                    """, status, adminId, handleResult, reportId);
            if ("RESOLVED".equals(status)) {
                applyReportAction(jdbc, reportId, adminAccountId, report, request, handleResult);
            }
            adminLogService.record(adminId, "REPORT_HANDLE", "REPORT_COMPLAINT", reportId, before, status);
            notificationDispatcher.dispatchToMember(
                    values.number(report.get("reporterId"), 0L),
                    "REPORT_RESULT",
                    "Report handled",
                    "Your report has been handled. Result: " + status + ". " + handleResult,
                    String.valueOf(report.get("targetType")),
                    values.number(report.get("targetId"), reportId)
            );
            return values.map("id", reportId, "status", status);
        });
    }

    private Map<String, Object> reportForUpdate(JdbcTemplate jdbc, Long reportId) {
        try {
            return jdbc.queryForObject("""
                    SELECT reporter_id, report_type, target_type, target_id, status
                    FROM report_complaint
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "reporterId", rs.getLong("reporter_id"),
                    "reportType", rs.getString("report_type"),
                    "targetType", rs.getString("target_type"),
                    "targetId", rs.getLong("target_id"),
                    "status", rs.getString("status")
            ), reportId);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Report does not exist");
        }
    }

    private void applyReportAction(JdbcTemplate jdbc, Long reportId, Long adminAccountId, Map<String, Object> report, Map<String, Object> request, String reason) {
        String targetType = String.valueOf(report.get("targetType"));
        Long targetId = values.number(report.get("targetId"), 0L);
        String requestedAction = values.firstNonBlank(values.value(request, "action", ""), defaultAction(report));
        switch (requestedAction) {
            case "delete-comment" -> {
                Long offenderId = commentMemberForUpdate(jdbc, targetId);
                jdbc.update("""
                        UPDATE comment_info
                        SET status = 'DELETED', deleted_at = NOW(3)
                        WHERE id = ?
                        """, targetId);
                deductViolation(offenderId, pointRules.commentDeletePenalty(), "COMMENT_DELETE_PENALTY",
                        "COMMENT", targetId, adminAccountId, reason, "report-comment-delete:" + reportId);
            }
            case "offline-resource" -> resourceService.transitionResourceByAdmin(targetId, adminAccountId, "OFFLINE", Map.of("reason", reason));
            case "copyright-down-resource" -> resourceService.transitionResourceByAdmin(targetId, adminAccountId, "COPYRIGHT_DOWN", Map.of("reason", reason));
            case "close-request" -> {
                int reward = requestRewardPoints(jdbc, targetId);
                requestRewardService.closeRequestByAdmin(adminAccountId, targetId, Map.of("reason", reason, "rewardDisposition", "COLLECT"));
                if (reward <= 0) {
                    deductViolation(requestOwner(jdbc, targetId), pointRules.violationPenalty(), "VIOLATION_CONFIRMED",
                            "REQUEST_POST", targetId, adminAccountId, reason, "report-request-close:" + reportId);
                }
            }
            case "delete-reply" -> {
                Long offenderId = replyMember(jdbc, targetId);
                requestRewardService.deleteReplyByAdmin(adminAccountId, targetId);
                deductViolation(offenderId, pointRules.violationPenalty(), "VIOLATION_CONFIRMED",
                        "REQUEST_REPLY", targetId, adminAccountId, reason, "report-reply-delete:" + reportId);
            }
            case "disable-user" -> {
                adminMemberService.disableMember(adminAccountId, targetId, Map.of("reason", reason));
                deductViolation(targetId, pointRules.violationPenalty(), "VIOLATION_CONFIRMED",
                        "USER", targetId, adminAccountId, reason, "report-user-disable:" + reportId);
            }
            default -> {
                if ("COMMENT".equals(targetType)) {
                    Long offenderId = commentMemberForUpdate(jdbc, targetId);
                    jdbc.update("UPDATE comment_info SET status = 'DELETED', deleted_at = NOW(3) WHERE id = ?", targetId);
                    deductViolation(offenderId, pointRules.commentDeletePenalty(), "COMMENT_DELETE_PENALTY",
                            "COMMENT", targetId, adminAccountId, reason, "report-comment-delete:" + reportId);
                }
            }
        }
    }

    private void deductViolation(Long memberId, int points, String scene, String relatedType, Long relatedId, Long operatorId, String reason, String bizKey) {
        if (memberId == null || memberId == 0 || points <= 0) {
            return;
        }
        pointManager.deduct(memberId, points, scene, relatedType, relatedId, operatorId, reason, bizKey);
    }

    private Long commentMemberForUpdate(JdbcTemplate jdbc, Long commentId) {
        return jdbc.queryForObject("SELECT member_id FROM comment_info WHERE id = ? FOR UPDATE", Long.class, commentId);
    }

    private Long replyMember(JdbcTemplate jdbc, Long replyId) {
        return jdbc.queryForObject("SELECT replier_id FROM request_reply WHERE id = ?", Long.class, replyId);
    }

    private Long requestOwner(JdbcTemplate jdbc, Long requestId) {
        return jdbc.queryForObject("SELECT requester_id FROM request_post WHERE id = ?", Long.class, requestId);
    }

    private int requestRewardPoints(JdbcTemplate jdbc, Long requestId) {
        Integer reward = jdbc.queryForObject("SELECT reward_points FROM request_post WHERE id = ?", Integer.class, requestId);
        return reward == null ? 0 : reward;
    }

    private String defaultAction(Map<String, Object> report) {
        String reportType = String.valueOf(report.get("reportType"));
        String targetType = String.valueOf(report.get("targetType"));
        if ("COPYRIGHT".equals(reportType)) {
            return "copyright-down-resource";
        }
        return switch (targetType) {
            case "COMMENT" -> "delete-comment";
            case "REQUEST_POST" -> "close-request";
            case "REQUEST_REPLY" -> "delete-reply";
            case "USER" -> "disable-user";
            default -> "offline-resource";
        };
    }

    private String reportTarget(String target) {
        return switch (values.firstNonBlank(target).toUpperCase()) {
            case "DEMAND", "REQUEST", "REQUEST_POST" -> "REQUEST_POST";
            case "COMMENT" -> "COMMENT";
            case "REQUEST_REPLY" -> "REQUEST_REPLY";
            case "USER" -> "USER";
            default -> "RESOURCE";
        };
    }

    private String reportType(String type, String targetType) {
        String normalized = values.firstNonBlank(type).toUpperCase();
        if ("COPYRIGHT".equals(normalized)) {
            return "COPYRIGHT";
        }
        return switch (normalized) {
            case "COMMENT" -> "COMMENT";
            case "REQUEST", "DEMAND", "REQUEST_POST" -> "REQUEST_POST";
            case "REQUEST_REPLY" -> "REQUEST_REPLY";
            case "USER" -> "USER";
            default -> targetType;
        };
    }
}

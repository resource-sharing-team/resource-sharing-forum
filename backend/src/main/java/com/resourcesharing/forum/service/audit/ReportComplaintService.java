package com.resourcesharing.forum.service.audit;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
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
public class ReportComplaintService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final AdminLogService adminLogService;

    public ReportComplaintService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            AdminLogService adminLogService
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.adminLogService = adminLogService;
    }

    public Map<String, Object> report(Long accountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("ok", true, "status", "PENDING");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
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
        return values.map("ok", true, "id", values.key(keyHolder), "status", "PENDING");
    }

    public Map<String, Object> handleReport(Long reportId, Long adminAccountId, Map<String, Object> request) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", reportId, "status", "RESOLVED");
        }
        return txSupport.required(() -> {
            Long adminId = lookup.adminProfileId(adminAccountId);
            String before = reportStatusForUpdate(jdbc, reportId);
            String status = values.firstNonBlank(values.value(request, "status", ""), values.value(request, "result", ""), "RESOLVED").toUpperCase();
            if (!List.of("RESOLVED", "REJECTED", "PROCESSING").contains(status)) {
                status = "RESOLVED";
            }
            jdbc.update("""
                    UPDATE report_complaint
                    SET status = ?, handler_id = ?, handle_result = ?, handle_time = NOW(3)
                    WHERE id = ? AND deleted_at IS NULL
                    """, status, adminId, values.firstNonBlank(values.value(request, "handleResult", ""), values.value(request, "reason", ""), "Admin handled report"), reportId);
            adminLogService.record(adminId, "REPORT_HANDLE", "REPORT_COMPLAINT", reportId, before, status);
            return values.map("id", reportId, "status", status);
        });
    }

    private String reportStatusForUpdate(JdbcTemplate jdbc, Long reportId) {
        try {
            return jdbc.queryForObject("""
                    SELECT status
                    FROM report_complaint
                    WHERE id = ? AND deleted_at IS NULL
                    FOR UPDATE
                    """, String.class, reportId);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Report does not exist");
        }
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

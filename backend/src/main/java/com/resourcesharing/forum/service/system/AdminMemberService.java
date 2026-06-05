package com.resourcesharing.forum.service.system;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdminMemberService {
    private final TxSupport txSupport;
    private final ValueSupport values;
    private final ForumLookupService lookup;
    private final AdminLogService adminLogService;
    private final NotificationDispatcher notificationDispatcher;

    public AdminMemberService(
            TxSupport txSupport,
            ValueSupport values,
            ForumLookupService lookup,
            AdminLogService adminLogService,
            NotificationDispatcher notificationDispatcher
    ) {
        this.txSupport = txSupport;
        this.values = values;
        this.lookup = lookup;
        this.adminLogService = adminLogService;
        this.notificationDispatcher = notificationDispatcher;
    }

    public Map<String, Object> disableMember(Long adminAccountId, Long memberId, Map<String, Object> request) {
        String reason = values.firstNonBlank(values.value(request, "reason", ""), "Admin disabled member");
        return updateMemberStatus(adminAccountId, memberId, "DISABLED", reason);
    }

    public Map<String, Object> enableMember(Long adminAccountId, Long memberId) {
        return updateMemberStatus(adminAccountId, memberId, "NORMAL", "Admin enabled member");
    }

    private Map<String, Object> updateMemberStatus(Long adminAccountId, Long memberId, String status, String reason) {
        JdbcTemplate jdbc = txSupport.jdbc();
        if (jdbc == null) {
            return values.map("id", memberId, "status", status);
        }
        return txSupport.required(() -> {
            Map<String, Object> account = memberAccountForUpdate(jdbc, memberId);
            if (!"USER".equals(account.get("role"))) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot manage administrator accounts through member endpoints");
            }
            String before = String.valueOf(account.get("status"));
            Long accountId = values.number(account.get("accountId"), 0L);
            jdbc.update("UPDATE user_account SET status = ? WHERE id = ?", status, accountId);
            adminLogService.record(lookup.adminProfileId(adminAccountId), "MEMBER_" + status, "MEMBER", memberId, before, status);
            notificationDispatcher.dispatchToMember(
                    memberId,
                    "MEMBER_STATUS",
                    "Account status updated",
                    "Your account status changed to " + status + ". Reason: " + reason,
                    "MEMBER",
                    memberId
            );
            return values.map("id", memberId, "accountId", accountId, "status", status, "reason", reason);
        });
    }

    private Map<String, Object> memberAccountForUpdate(JdbcTemplate jdbc, Long memberId) {
        try {
            return jdbc.queryForObject("""
                    SELECT ua.id AS account_id, ua.role, ua.status
                    FROM member_profile mp
                    JOIN user_account ua ON ua.id = mp.account_id
                    WHERE mp.id = ? AND mp.deleted_at IS NULL AND ua.deleted_at IS NULL
                    FOR UPDATE
                    """, (rs, rowNum) -> values.map(
                    "accountId", rs.getLong("account_id"),
                    "role", rs.getString("role"),
                    "status", rs.getString("status")
            ), memberId);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Member does not exist");
        }
    }
}

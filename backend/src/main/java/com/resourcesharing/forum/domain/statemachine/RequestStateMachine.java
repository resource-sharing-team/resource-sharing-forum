package com.resourcesharing.forum.domain.statemachine;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;

import java.util.Locale;
import java.util.Set;

public final class RequestStateMachine {
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "AUDITOR");
    private static final Set<String> STATUSES = Set.of("ONGOING", "RESOLVED", "CANCELLED", "CLOSED");

    private RequestStateMachine() {
    }

    public static void assertCanTransit(
            String currentStatus,
            String targetStatus,
            String action,
            String operatorRole,
            boolean isOwner,
            String reason
    ) {
        String from = normalizeStatus(currentStatus);
        String to = normalizeStatus(targetStatus);
        String normalizedAction = normalizeAction(action);
        validateKnownStatus(from);
        validateKnownStatus(to);
        if (!canTransit(from, to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid request status transition: " + from + " -> " + to);
        }
        if (requiresAdmin(normalizedAction) && !isAdmin(operatorRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Request status action requires admin permission");
        }
        if (requiresOwner(normalizedAction) && !isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the request owner can perform this action");
        }
        if (requiresReason(normalizedAction) && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Reason is required for this request status action");
        }
    }

    public static boolean canTransit(String currentStatus, String targetStatus) {
        String from = normalizeStatus(currentStatus);
        String to = normalizeStatus(targetStatus);
        if (from.equals(to)) {
            return false;
        }
        return switch (to) {
            case "RESOLVED", "CANCELLED", "CLOSED" -> "ONGOING".equals(from);
            case "ONGOING" -> "CLOSED".equals(from);
            default -> false;
        };
    }

    private static boolean requiresAdmin(String action) {
        return Set.of("CLOSE", "RESTORE").contains(action);
    }

    private static boolean requiresOwner(String action) {
        return Set.of("ACCEPT_REPLY", "CANCEL").contains(action);
    }

    private static boolean requiresReason(String action) {
        return "CLOSE".equals(action);
    }

    private static boolean isAdmin(String role) {
        return role != null && ADMIN_ROLES.contains(role.toUpperCase(Locale.ROOT));
    }

    private static void validateKnownStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown request status: " + status);
        }
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
    }
}

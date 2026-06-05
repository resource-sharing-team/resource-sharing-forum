package com.resourcesharing.forum.domain.statemachine;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;

import java.util.Locale;
import java.util.Set;

public final class ResourceStateMachine {
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "AUDITOR");
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "PENDING_REVIEW", "PUBLISHED", "REJECTED", "REVIEWING_RISK", "OFFLINE", "COPYRIGHT_DOWN", "DELETED"
    );

    private ResourceStateMachine() {
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid resource status transition: " + from + " -> " + to);
        }
        if (requiresAdmin(normalizedAction) && !isAdmin(operatorRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Resource status action requires admin permission");
        }
        if (requiresOwner(normalizedAction) && !isOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the resource owner can perform this action");
        }
        if (requiresReason(normalizedAction, to) && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Reason is required for this resource status action");
        }
    }

    public static boolean canTransit(String currentStatus, String targetStatus) {
        String from = normalizeStatus(currentStatus);
        String to = normalizeStatus(targetStatus);
        if (from.equals(to)) {
            return false;
        }
        return switch (to) {
            case "DRAFT" -> Set.of("PENDING_REVIEW", "REJECTED").contains(from);
            case "PENDING_REVIEW" -> Set.of("DRAFT", "REJECTED", "OFFLINE").contains(from);
            case "PUBLISHED" -> Set.of("PENDING_REVIEW", "OFFLINE", "COPYRIGHT_DOWN", "REVIEWING_RISK").contains(from);
            case "REJECTED" -> Set.of("PENDING_REVIEW", "REVIEWING_RISK").contains(from);
            case "REVIEWING_RISK" -> "PUBLISHED".equals(from);
            case "OFFLINE", "COPYRIGHT_DOWN" -> Set.of("PUBLISHED", "REVIEWING_RISK").contains(from);
            case "DELETED" -> !"DELETED".equals(from);
            default -> false;
        };
    }

    public static String targetStatusForAction(String action) {
        return switch (normalizeAction(action)) {
            case "APPROVE", "APPROVED", "RESTORE", "RESTORED", "RISK_CLEAR", "COPYRIGHT_CLEAR" -> "PUBLISHED";
            case "SUBMIT" -> "PENDING_REVIEW";
            case "WITHDRAW" -> "DRAFT";
            case "REJECT", "REJECTED" -> "REJECTED";
            case "RISK", "RISK_REVIEW", "REVIEWING_RISK" -> "REVIEWING_RISK";
            case "OFFLINE" -> "OFFLINE";
            case "COPYRIGHT", "COPYRIGHT_DOWN" -> "COPYRIGHT_DOWN";
            case "DELETE", "DELETED" -> "DELETED";
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported resource status action: " + action);
        };
    }

    private static boolean requiresAdmin(String action) {
        return Set.of("APPROVE", "APPROVED", "REJECT", "REJECTED", "OFFLINE", "COPYRIGHT", "COPYRIGHT_DOWN",
                "RESTORE", "RESTORED", "RISK", "RISK_REVIEW", "REVIEWING_RISK", "RISK_CLEAR", "COPYRIGHT_CLEAR").contains(action);
    }

    private static boolean requiresOwner(String action) {
        return Set.of("SUBMIT", "WITHDRAW").contains(action);
    }

    private static boolean requiresReason(String action, String targetStatus) {
        return Set.of("REJECT", "REJECTED", "OFFLINE", "COPYRIGHT", "COPYRIGHT_DOWN").contains(action)
                || Set.of("REJECTED", "OFFLINE", "COPYRIGHT_DOWN").contains(targetStatus);
    }

    private static boolean isAdmin(String role) {
        return role != null && ADMIN_ROLES.contains(role.toUpperCase(Locale.ROOT));
    }

    private static void validateKnownStatus(String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unknown resource status: " + status);
        }
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
    }
}

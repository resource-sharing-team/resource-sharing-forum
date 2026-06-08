package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.domain.statemachine.ResourceStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceStateMachineTest {

    @Test
    void allowsDesignSpecResourceTransitions() {
        assertThat(ResourceStateMachine.canTransit("PENDING_REVIEW", "PUBLISHED")).isTrue();
        assertThat(ResourceStateMachine.canTransit("PENDING_REVIEW", "REJECTED")).isTrue();
        assertThat(ResourceStateMachine.canTransit("PENDING_REVIEW", "DRAFT")).isTrue();
        assertThat(ResourceStateMachine.canTransit("REJECTED", "PENDING_REVIEW")).isTrue();
        assertThat(ResourceStateMachine.canTransit("PUBLISHED", "REVIEWING_RISK")).isTrue();
        assertThat(ResourceStateMachine.canTransit("PUBLISHED", "OFFLINE")).isTrue();
        assertThat(ResourceStateMachine.canTransit("PUBLISHED", "COPYRIGHT_DOWN")).isTrue();
        assertThat(ResourceStateMachine.canTransit("OFFLINE", "PENDING_REVIEW")).isTrue();
        assertThat(ResourceStateMachine.canTransit("OFFLINE", "PUBLISHED")).isTrue();
        assertThat(ResourceStateMachine.canTransit("COPYRIGHT_DOWN", "DELETED")).isTrue();
    }

    @Test
    void rejectsInvalidResourceTransitions() {
        assertThat(ResourceStateMachine.canTransit("PUBLISHED", "REJECTED")).isFalse();
        assertThat(ResourceStateMachine.canTransit("DELETED", "PUBLISHED")).isFalse();
        assertThat(ResourceStateMachine.canTransit("DRAFT", "PUBLISHED")).isFalse();
    }

    @Test
    void requiresAdminAndReasonForRejectAndOffline() {
        ResourceStateMachine.assertCanTransit("PENDING_REVIEW", "PUBLISHED", "APPROVE", "ADMIN", false, "");

        assertThatThrownBy(() -> ResourceStateMachine.assertCanTransit(
                "PENDING_REVIEW", "REJECTED", "REJECT", "ADMIN", false, ""))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> ResourceStateMachine.assertCanTransit(
                "PUBLISHED", "OFFLINE", "OFFLINE", "MEMBER", false, "violation"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requiresOwnerForSubmitAndWithdraw() {
        ResourceStateMachine.assertCanTransit("REJECTED", "PENDING_REVIEW", "SUBMIT", "MEMBER", true, "");
        ResourceStateMachine.assertCanTransit("PENDING_REVIEW", "DRAFT", "WITHDRAW", "MEMBER", true, "");

        assertThatThrownBy(() -> ResourceStateMachine.assertCanTransit(
                "REJECTED", "PENDING_REVIEW", "SUBMIT", "MEMBER", false, ""))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> ResourceStateMachine.assertCanTransit(
                "PENDING_REVIEW", "DRAFT", "WITHDRAW", "MEMBER", false, ""))
                .isInstanceOf(BusinessException.class);
    }
}

package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.domain.statemachine.RequestStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestStateMachineTest {

    @Test
    void allowsDesignSpecRequestTransitions() {
        assertThat(RequestStateMachine.canTransit("ONGOING", "RESOLVED")).isTrue();
        assertThat(RequestStateMachine.canTransit("ONGOING", "CANCELLED")).isTrue();
        assertThat(RequestStateMachine.canTransit("ONGOING", "CLOSED")).isTrue();
        assertThat(RequestStateMachine.canTransit("CLOSED", "ONGOING")).isTrue();
    }

    @Test
    void rejectsInvalidRequestTransitions() {
        assertThat(RequestStateMachine.canTransit("CANCELLED", "RESOLVED")).isFalse();
        assertThat(RequestStateMachine.canTransit("RESOLVED", "ONGOING")).isFalse();
        assertThat(RequestStateMachine.canTransit("CANCELLED", "ONGOING")).isFalse();
    }

    @Test
    void enforcesOwnerAndAdminRules() {
        RequestStateMachine.assertCanTransit("ONGOING", "RESOLVED", "ACCEPT_REPLY", "MEMBER", true, "");
        RequestStateMachine.assertCanTransit("ONGOING", "CANCELLED", "CANCEL", "MEMBER", true, "");
        RequestStateMachine.assertCanTransit("CLOSED", "ONGOING", "RESTORE", "ADMIN", false, "");

        assertThatThrownBy(() -> RequestStateMachine.assertCanTransit(
                "ONGOING", "RESOLVED", "ACCEPT_REPLY", "MEMBER", false, ""))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> RequestStateMachine.assertCanTransit(
                "ONGOING", "CLOSED", "CLOSE", "MEMBER", false, "violation"))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> RequestStateMachine.assertCanTransit(
                "ONGOING", "CLOSED", "CLOSE", "ADMIN", false, ""))
                .isInstanceOf(BusinessException.class);
    }
}

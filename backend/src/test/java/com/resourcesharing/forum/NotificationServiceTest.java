package com.resourcesharing.forum;

import com.resourcesharing.forum.service.notification.NotificationService;
import com.resourcesharing.forum.service.support.ForumLookupService;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ForumLookupService lookup = mock(ForumLookupService.class);
    private final NotificationService notificationService = new NotificationService(
            new InlineTxSupport(jdbc),
            new ValueSupport(),
            lookup
    );

    @Test
    void unreadCountReturnsZeroForAuthenticatedMemberWithEmptyInbox() {
        when(lookup.requireMemberId(3L)).thenReturn(2L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(2L))).thenReturn(0);

        int count = notificationService.unreadCount(3L);

        assertThat(count).isZero();
        verify(lookup).requireMemberId(3L);
    }

    private static class InlineTxSupport extends TxSupport {
        private final JdbcTemplate jdbc;

        InlineTxSupport(JdbcTemplate jdbc) {
            super(provider(jdbc), provider(null));
            this.jdbc = jdbc;
        }

        @Override
        public JdbcTemplate jdbc() {
            return jdbc;
        }

        private static <T> ObjectProvider<T> provider(T value) {
            return new ObjectProvider<>() {
                @Override
                public T getObject(Object... args) throws BeansException {
                    return value;
                }

                @Override
                public T getIfAvailable() throws BeansException {
                    return value;
                }

                @Override
                public T getIfUnique() throws BeansException {
                    return value;
                }

                @Override
                public T getObject() throws BeansException {
                    return value;
                }
            };
        }
    }
}

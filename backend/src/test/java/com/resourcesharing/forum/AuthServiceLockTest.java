package com.resourcesharing.forum;

import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.security.JwtProperties;
import com.resourcesharing.forum.security.JwtService;
import com.resourcesharing.forum.service.identity.AuthService;
import com.resourcesharing.forum.service.identity.EmailCodeService;
import com.resourcesharing.forum.service.support.MappingSupport;
import com.resourcesharing.forum.service.support.TxSupport;
import com.resourcesharing.forum.service.support.ValueSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLockTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthService authService = new AuthService(
            new InlineTxSupport(jdbc),
            new ValueSupport(),
            mock(MappingSupport.class),
            new JwtService(new JwtProperties("resource-sharing-forum-dev-secret-must-be-changed-2026", 120)),
            new JwtProperties("resource-sharing-forum-dev-secret-must-be-changed-2026", 120),
            passwordEncoder,
            mock(EmailCodeService.class)
    );

    @Test
    void fifthFailedLoginLocksAccountAndRecordsLockedResult() {
        stubAccountRow("lock_user", 4, "NORMAL", null);
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(Map.of("account", "lock_user", "password", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("\u8d26\u53f7\u4e34\u65f6\u9501\u5b9a");

        verify(jdbc).update(
                ArgumentMatchers.contains("status = IF"),
                eq(5),
                eq(5),
                eq(5),
                eq(5),
                eq(5),
                eq(99L)
        );
        verify(jdbc).update(
                ArgumentMatchers.contains("INSERT INTO login_record"),
                eq(99L),
                eq("lock_user"),
                eq("LOCKED"),
                eq("ACCOUNT_LOCKED")
        );
    }

    @Test
    void correctPasswordIsRejectedWhenFailedCountAlreadyReachedThreshold() {
        stubAccountRow("lock_user", 5, "NORMAL", null);

        assertThatThrownBy(() -> authService.login(Map.of("account", "lock_user", "password", "abc123456")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("\u8d26\u53f7\u4e34\u65f6\u9501\u5b9a");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jdbc, atLeastOnce()).update(
                ArgumentMatchers.contains("SET status = 'LOCKED'"),
                eq(5),
                isNull(),
                isNull(),
                eq(99L)
        );
    }

    private void stubAccountRow(String account, int failedLoginCount, String status, LocalDateTime lockedUntil) {
        Map<String, Object> row = Map.of(
                "id", 99L,
                "username", account,
                "email", account + "@example.com",
                "passwordHash", "encoded-password",
                "role", "USER",
                "status", status,
                "failedLoginCount", failedLoginCount,
                "lockedUntil", lockedUntil == null ? "" : lockedUntil
        );
        if (lockedUntil == null) {
            row = new java.util.HashMap<>(row);
            row.put("lockedUntil", null);
        }
        when(jdbc.queryForObject(
                anyString(),
                ArgumentMatchers.<RowMapper<Map<String, Object>>>any(),
                eq(account),
                eq(account)
        )).thenReturn(row);
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

        @Override
        public <T> T required(Supplier<T> action) {
            return action.get();
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

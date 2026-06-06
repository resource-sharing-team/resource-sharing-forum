package com.resourcesharing.forum;

import com.resourcesharing.forum.security.JwtAuthenticationFilter;
import com.resourcesharing.forum.security.JwtProperties;
import com.resourcesharing.forum.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jwtAuthenticationRejectsTemporarilyLockedAccounts() throws Exception {
        JwtService jwtService = new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef", 120));
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate(0);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, provider(jdbc));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String token = jwtService.generate("1", "MEMBER");

        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());

        assertThat(jdbc.sql()).contains(
                "status = 'NORMAL'",
                "deleted_at IS NULL",
                "locked_until IS NULL OR locked_until <= NOW(3)"
        );
        assertThat(jdbc.args()).containsExactly(1L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private final Integer result;
        private String sql;
        private Object[] args;

        private CapturingJdbcTemplate(Integer result) {
            this.result = result;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.sql = sql;
            this.args = args;
            return requiredType.cast(result);
        }

        private String sql() {
            return sql;
        }

        private Object[] args() {
            return args;
        }
    }

    private static ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbc) {
        return new ObjectProvider<>() {
            @Override
            public JdbcTemplate getObject(Object... args) {
                return jdbc;
            }

            @Override
            public JdbcTemplate getIfAvailable() {
                return jdbc;
            }

            @Override
            public JdbcTemplate getIfUnique() {
                return jdbc;
            }

            @Override
            public JdbcTemplate getObject() {
                return jdbc;
            }
        };
    }
}

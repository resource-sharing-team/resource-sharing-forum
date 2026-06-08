package com.resourcesharing.forum;

import com.resourcesharing.forum.config.SecurityHeadersFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersFilterTest {
    @Test
    void writesDeploymentSecurityHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityHeadersFilter().doFilter(
                new MockHttpServletRequest("GET", "/api/health"),
                response,
                new MockFilterChain()
        );

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).contains("geolocation=()");
        assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
    }
}

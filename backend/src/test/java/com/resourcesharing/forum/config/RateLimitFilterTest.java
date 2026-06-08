package com.resourcesharing.forum.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {
    private final RateLimitFilter filter = new RateLimitFilter(60, 2,
            Clock.fixed(Instant.parse("2026-06-06T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void limitsSensitiveAuthRequestsByClientAndPath() throws Exception {
        assertAllowed(authRequest());
        assertAllowed(authRequest());

        MockHttpServletResponse blocked = perform(authRequest());

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("\"code\":429", "too many requests");
    }

    @Test
    void doesNotLimitNonSensitiveReadRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resources");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = perform(request);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertAllowed(MockHttpServletRequest request) throws Exception {
        assertThat(perform(request).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse perform(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest authRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}

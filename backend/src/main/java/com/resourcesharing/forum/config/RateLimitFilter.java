package com.resourcesharing.forum.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {
    private final int authWindowSeconds;
    private final int authMaxRequests;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(
            @Value("${forum.rate-limit.auth-window-seconds:60}") int authWindowSeconds,
            @Value("${forum.rate-limit.auth-max-requests:30}") int authMaxRequests) {
        this(authWindowSeconds, authMaxRequests, Clock.systemUTC());
    }

    RateLimitFilter(int authWindowSeconds, int authMaxRequests, Clock clock) {
        this.authWindowSeconds = Math.max(1, authWindowSeconds);
        this.authMaxRequests = Math.max(1, authMaxRequests);
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isSensitiveAuthRequest(request) || allow(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":429,\"message\":\"too many requests\",\"data\":null,\"timestamp\":\""
                + java.time.Instant.now(clock) + "\"}");
    }

    private boolean allow(HttpServletRequest request) {
        long window = java.time.Instant.now(clock).getEpochSecond() / authWindowSeconds;
        String key = clientKey(request) + ":" + request.getRequestURI() + ":" + window;
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(window));
        if (counters.size() > 2048) {
            counters.entrySet().removeIf(entry -> entry.getValue().window < window - 1);
        }
        return counter.count.incrementAndGet() <= authMaxRequests;
    }

    private static boolean isSensitiveAuthRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/auth/login")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/auth/reset-password")
                || path.equals("/api/v1/auth/reset-password")
                || path.equals("/api/auth/reset-password/code")
                || path.equals("/api/v1/auth/reset-password/code");
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record WindowCounter(long window, AtomicInteger count) {
        private WindowCounter(long window) {
            this(window, new AtomicInteger());
        }
    }
}

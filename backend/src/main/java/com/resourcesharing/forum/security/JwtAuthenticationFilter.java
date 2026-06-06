package com.resourcesharing.forum.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ObjectProvider<JdbcTemplate> jdbcProvider;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jwtService = jwtService;
        this.jdbcProvider = jdbcProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                jwtService.parse(header.substring(7)).ifPresent(claims -> {
                    if (accountActive(claims.subject())) {
                        String role = claims.role() == null ? "USER" : claims.role();
                        var authority = new SimpleGrantedAuthority("ROLE_" + role);
                        var authentication = new UsernamePasswordAuthenticationToken(claims.subject(), null, List.of(authority));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        MDC.put("userId", claims.subject());
                    }
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }

    private boolean accountActive(String subject) {
        JdbcTemplate jdbc = jdbcProvider.getIfAvailable();
        if (jdbc == null) {
            return true;
        }
        try {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM user_account
                    WHERE id = ? AND status = 'NORMAL' AND deleted_at IS NULL
                      AND (locked_until IS NULL OR locked_until <= NOW(3))
                    """, Integer.class, Long.parseLong(subject));
            return count != null && count > 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}


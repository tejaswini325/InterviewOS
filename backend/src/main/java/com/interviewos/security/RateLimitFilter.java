package com.interviewos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.config.AppProperties;
import com.interviewos.config.KeyValueStore;
import com.interviewos.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fixed-window limiter (per client IP, per minute) for login and registration, backed by Redis. */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final KeyValueStore store;
    private final AppProperties props;
    private final ObjectMapper mapper;

    public RateLimitFilter(KeyValueStore store, AppProperties props, ObjectMapper mapper) {
        this.store = store; this.props = props; this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        return !("POST".equals(r.getMethod()) && (r.getRequestURI().equals("/api/auth/login") || r.getRequestURI().equals("/api/auth/register")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long count = 0;
        try {
            count = store.increment("rl:" + req.getRequestURI() + ":" + req.getRemoteAddr(), Duration.ofMinutes(1));
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
        }
        if (count > props.rateLimit().authRequestsPerMinute()) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            mapper.writeValue(res.getOutputStream(), ApiError.of(429, "RATE_LIMITED", "Too many attempts. Try again in a minute."));
            return;
        }
        chain.doFilter(req, res);
    }
}

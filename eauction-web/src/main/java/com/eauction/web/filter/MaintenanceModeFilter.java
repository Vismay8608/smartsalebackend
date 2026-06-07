package com.eauction.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Covers: MaintenanceModeFilter
 *
 * Reads a Redis flag to decide whether the platform is in maintenance mode.
 * When active, all endpoints except the health check return 503.
 *
 * Enable:   SET app:maintenance:active "true" EX 7200   (2-hour window)
 * Message:  SET app:maintenance:message "Scheduled maintenance 02:00–04:00 UTC"
 * Disable:  DEL app:maintenance:active
 *
 * Fails OPEN (passes requests through) if Redis is unavailable.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
@RequiredArgsConstructor
public class MaintenanceModeFilter extends OncePerRequestFilter {

    static final String KEY_ACTIVE  = "app:maintenance:active";
    static final String KEY_MESSAGE = "app:maintenance:message";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        try {
            String active = redisTemplate.opsForValue().get(KEY_ACTIVE);
            if ("true".equalsIgnoreCase(active)) {
                String msg = redisTemplate.opsForValue().get(KEY_MESSAGE);
                if (msg == null || msg.isBlank()) {
                    msg = "The platform is currently under maintenance. Please try again later.";
                }
                log.info("Maintenance mode active — rejecting [path={}]", request.getRequestURI());
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.setHeader("Retry-After", "3600");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                        "success",   false,
                        "code",      "SYS003",
                        "message",   msg,
                        "timestamp", Instant.now().toString())));
                return;
            }
        } catch (Exception ex) {
            log.error("Maintenance mode Redis check failed — failing open", ex);
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Always allow health checks through — load balancer probes must not be blocked
        return request.getRequestURI().startsWith("/actuator/health");
    }
}

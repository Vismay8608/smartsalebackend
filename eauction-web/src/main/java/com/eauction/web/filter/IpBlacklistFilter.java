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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Blocks requests from IPs present in the Redis SET  security:ip:blacklist.
 *
 * Add an IP:   SADD security:ip:blacklist 1.2.3.4
 * Remove an IP: SREM security:ip:blacklist 1.2.3.4
 *
 * Fails OPEN when Redis is unavailable — traffic is allowed through rather
 * than taking the site down for all users.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class IpBlacklistFilter extends OncePerRequestFilter {

    static final String BLACKLIST_KEY = "security:ip:blacklist";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String ip = resolveIp(request);

        try {
            Boolean blocked = redisTemplate.opsForSet().isMember(BLACKLIST_KEY, ip);
            if (Boolean.TRUE.equals(blocked)) {
                log.warn("Blocked blacklisted IP [ip={}] [path={}] [traceId={}]",
                        ip, request.getRequestURI(),
                        request.getAttribute(RequestTraceFilter.REQUEST_ATTR));
                sendError(response, HttpStatus.FORBIDDEN, "SEC001", "Access denied");
                return;
            }
        } catch (Exception ex) {
            // Redis unavailable — fail open, warn and continue
            log.error("IP blacklist Redis check failed — failing open [ip={}]", ip, ex);
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path   = request.getRequestURI();
        String method = request.getMethod();
        return HttpMethod.OPTIONS.matches(method)
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    static String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendError(HttpServletResponse response, HttpStatus status,
                           String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("success", false, "code", code, "message", message,
                       "timestamp", Instant.now().toString())));
    }
}

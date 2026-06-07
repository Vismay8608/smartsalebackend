package com.eauction.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eauction.web.service.JwtService;
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
import java.util.concurrent.TimeUnit;

/**
 * Redis fixed-window rate limiter.
 *
 * Three tiers:
 *   1. Login endpoint   — 5  req / min per IP       (supplements LoginAttemptService)
 *   2. Authenticated    — 120 req / min per username  (extracted from JWT without full validation)
 *   3. Unauthenticated  — 30  req / min per IP
 *
 * Returns HTTP 429 with Retry-After and X-RateLimit-* headers on breach.
 * Fails OPEN when Redis is unavailable.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // window size
    private static final int WINDOW_SECONDS  = 60;

    // limits
    private static final int LIMIT_LOGIN     = 5;    // per IP/min — login brute-force
    private static final int LIMIT_USER      = 120;  // per authenticated user/min
    private static final int LIMIT_ANON      = 30;   // per unauthenticated IP/min

    private static final String LOGIN_PATH   = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private final StringRedisTemplate redisTemplate;
    private final JwtService          jwtService;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String path   = request.getRequestURI();
        String ip     = IpBlacklistFilter.resolveIp(request);
        long   window = Instant.now().getEpochSecond() / WINDOW_SECONDS;

        // Determine bucket and limit
        String key;
        int    limit;

        if (LOGIN_PATH.equals(path) || REFRESH_PATH.equals(path)) {
            key   = "rl:auth:" + ip + ":" + window;
            limit = LIMIT_LOGIN;
        } else {
            String username = extractUsername(request);
            if (username != null) {
                key   = "rl:user:" + username + ":" + window;
                limit = LIMIT_USER;
            } else {
                key   = "rl:anon:" + ip + ":" + window;
                limit = LIMIT_ANON;
            }
        }

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // First hit in this window — set expiry (window + small buffer)
                redisTemplate.expire(key, WINDOW_SECONDS + 5L, TimeUnit.SECONDS);
            }

            long remaining = (count == null) ? limit : Math.max(0L, limit - count);
            long retryAfter = WINDOW_SECONDS - (Instant.now().getEpochSecond() % WINDOW_SECONDS);

            response.setHeader("X-RateLimit-Limit",     String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset",     String.valueOf(window + 1));

            if (count != null && count > limit) {
                log.warn("Rate limit exceeded [key={}] [count={}] [limit={}] [ip={}] [traceId={}]",
                        key, count, limit, ip,
                        request.getAttribute(RequestTraceFilter.REQUEST_ATTR));
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                sendError(response, HttpStatus.TOO_MANY_REQUESTS,
                        "SEC002", "Too many requests — please try again later.");
                return;
            }

        } catch (Exception ex) {
            // Redis unavailable — fail open
            log.error("Rate limit Redis operation failed — failing open [key={}]", key, ex);
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

    /** Extract username from the Bearer token if present — no full auth, just claim reading. */
    private String extractUsername(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                if (jwtService.isValid(token)) {
                    return jwtService.extractUsername(token);
                }
            }
        } catch (Exception ignored) {
            // Malformed or expired JWT — treat as unauthenticated
        }
        return null;
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

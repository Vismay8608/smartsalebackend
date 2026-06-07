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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Covers: IdempotencyFilter + ReplayAttackProtectionFilter
 *
 * Prevents duplicate execution of state-changing requests — critical for
 * fintech operations (bids, payments, registrations).
 *
 * Client includes header:  Idempotency-Key: <UUID or unique string ≤64 chars>
 * Response echoes header:  Idempotency-Key: <same key>
 *                          X-Idempotency-Replayed: true | false
 *
 * First request: processed normally, result cached in Redis for 24 h.
 * Repeat request (same key, same user): cached response returned immediately.
 * Concurrent duplicate: 409 Conflict.
 * Key reused for different path: 422 Unprocessable Entity.
 *
 * Fails OPEN if Redis is unavailable — the request is processed normally.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 12)
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER_KEY      = "Idempotency-Key";
    public static final String HEADER_REPLAYED = "X-Idempotency-Replayed";

    private static final Duration TTL = Duration.ofHours(24);

    private static final Set<String> IDEMPOTENT_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/logout-all"
    );

    private final StringRedisTemplate redisTemplate;
    private final JwtService          jwtService;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String key = request.getHeader(HEADER_KEY);
        if (key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (key.length() > 64) {
            sendError(response, HttpStatus.BAD_REQUEST, "IDMP001",
                    "Idempotency-Key must not exceed 64 characters");
            return;
        }

        String owner    = resolveOwner(request);
        String redisKey = "idempotent:" + owner + ":" + key;

        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(redisKey);

            if (!cached.isEmpty()) {
                String status = (String) cached.get("status");

                if ("IN_PROGRESS".equals(status)) {
                    log.warn("Duplicate in-flight idempotent request [key={}] [owner={}]", key, owner);
                    sendError(response, HttpStatus.CONFLICT, "IDMP002",
                            "A request with this Idempotency-Key is still being processed");
                    return;
                }

                if ("COMPLETED".equals(status)) {
                    String storedPath = (String) cached.get("path");
                    if (!request.getRequestURI().equals(storedPath)) {
                        sendError(response, HttpStatus.UNPROCESSABLE_ENTITY, "IDMP003",
                                "Idempotency-Key already used for a different endpoint");
                        return;
                    }
                    // Replay cached response
                    int    code    = Integer.parseInt((String) cached.get("statusCode"));
                    String body    = (String) cached.get("responseBody");
                    String ct      = (String) cached.getOrDefault("contentType",
                                                MediaType.APPLICATION_JSON_VALUE);
                    log.debug("Replaying idempotent response [key={}] [owner={}] [status={}]",
                            key, owner, code);
                    response.setStatus(code);
                    response.setContentType(ct);
                    response.setHeader(HEADER_KEY,      key);
                    response.setHeader(HEADER_REPLAYED, "true");
                    if (body != null) response.getWriter().write(body);
                    return;
                }
            }

            // Mark in-progress
            redisTemplate.opsForHash().putAll(redisKey, Map.of(
                    "status",    "IN_PROGRESS",
                    "path",      request.getRequestURI(),
                    "method",    request.getMethod(),
                    "createdAt", Instant.now().toString()));
            redisTemplate.expire(redisKey, TTL);

            var wrappedResp = new ContentCachingResponseWrapper(response);
            try {
                chain.doFilter(request, wrappedResp);
            } finally {
                String body = new String(wrappedResp.getContentAsByteArray(), StandardCharsets.UTF_8);
                redisTemplate.opsForHash().putAll(redisKey, Map.of(
                        "status",       "COMPLETED",
                        "statusCode",   String.valueOf(wrappedResp.getStatus()),
                        "responseBody", body,
                        "contentType",  wrappedResp.getContentType() != null
                                            ? wrappedResp.getContentType()
                                            : MediaType.APPLICATION_JSON_VALUE,
                        "completedAt",  Instant.now().toString()));
                redisTemplate.expire(redisKey, TTL);
                wrappedResp.setHeader(HEADER_KEY,      key);
                wrappedResp.setHeader(HEADER_REPLAYED, "false");
                wrappedResp.copyBodyToResponse();
            }

        } catch (Exception ex) {
            log.error("Idempotency filter failed — failing open [key={}]", key, ex);
            try { redisTemplate.delete(redisKey); } catch (Exception ignored) {}
            chain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !IDEMPOTENT_METHODS.contains(request.getMethod().toUpperCase())
                || SKIP_PATHS.contains(path)
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private String resolveOwner(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                if (jwtService.isValid(token)) {
                    String username = jwtService.extractUsername(token);
                    if (username != null) return "user:" + username;
                }
            }
        } catch (Exception ignored) {}
        return "ip:" + IpBlacklistFilter.resolveIp(request);
    }

    private void sendError(HttpServletResponse response, HttpStatus status,
                           String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success",   false,
                "code",      code,
                "message",   message,
                "timestamp", Instant.now().toString())));
    }
}

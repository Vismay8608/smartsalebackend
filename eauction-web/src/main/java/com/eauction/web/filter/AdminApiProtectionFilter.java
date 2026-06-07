package com.eauction.web.filter;

import com.eauction.common.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Covers: AdminApiProtectionFilter
 *
 * Hardens the platform-admin surface (/api/v1/admin/**) with two
 * independent layers, both of which must pass:
 *
 *   1. Identity  — caller's verified JWT userType must be SYSTEM. A SELLER
 *      or BUYER token simply cannot reach these endpoints, regardless of
 *      whatever permissions a tampered/forged token might claim to carry
 *      (the JWT signature already guarantees userType is genuine).
 *
 *   2. Network   — optional IP allow-list (operations team enables this for
 *      production so the admin surface is reachable only from office/VPN
 *      ranges, even with valid SYSTEM credentials — protects against
 *      credential theft/phishing).
 *      Enable:  SET security:admin:ip:allowlist:active "true"
 *      Add IP:  SADD security:admin:ip:allowlist 10.0.0.5
 *
 * Runs inside the Spring Security chain (after JwtAuthFilter, so TenantContext
 * is populated) — NOT a servlet filter; SecurityConfig wires it via
 * addFilterAfter(...). Fails CLOSED on identity (deny if context missing) but
 * OPEN on the optional Redis-backed IP allow-list (so a Redis outage cannot
 * lock operators out of the admin console — identity check still applies).
 */
@Slf4j
@RequiredArgsConstructor
public class AdminApiProtectionFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/v1/admin/";
    private static final String SYSTEM_USER_TYPE  = "SYSTEM";

    static final String KEY_ALLOWLIST_ACTIVE = "security:admin:ip:allowlist:active";
    static final String KEY_ALLOWLIST        = "security:admin:ip:allowlist";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            // No (valid) credentials at all — let the standard chain produce the
            // platform's usual 401 AUTH005 "Authentication required" response
            // rather than confirming to an anonymous prober that a SYSTEM-only
            // endpoint exists at this path.
            chain.doFilter(request, response);
            return;
        }

        String userType = TenantContext.getUserType();
        if (userType == null || !SYSTEM_USER_TYPE.equals(userType)) {
            log.error("ADMIN ACCESS DENIED — non-SYSTEM principal attempted admin endpoint "
                            + "[user={}] [userType={}] [path={}] [ip={}] [traceId={}]",
                    TenantContext.getUsername(), userType, request.getRequestURI(),
                    IpBlacklistFilter.resolveIp(request), traceId(request));
            sendError(response, HttpStatus.FORBIDDEN, "SEC_ADMIN_FORBIDDEN",
                    "This endpoint is restricted to platform administrators");
            return;
        }

        try {
            String active = redisTemplate.opsForValue().get(KEY_ALLOWLIST_ACTIVE);
            if ("true".equalsIgnoreCase(active)) {
                String ip = IpBlacklistFilter.resolveIp(request);
                Boolean allowed = redisTemplate.opsForSet().isMember(KEY_ALLOWLIST, ip);
                if (!Boolean.TRUE.equals(allowed)) {
                    log.error("ADMIN ACCESS DENIED — IP not in admin allow-list "
                                    + "[user={}] [ip={}] [path={}] [traceId={}]",
                            TenantContext.getUsername(), ip, request.getRequestURI(), traceId(request));
                    sendError(response, HttpStatus.FORBIDDEN, "SEC_ADMIN_IP_BLOCKED",
                            "Admin access is not permitted from this network");
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("Admin IP allow-list check failed — failing open on network layer "
                    + "(identity check already passed)", ex);
        }

        chain.doFilter(request, response);
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

    private Object traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(RequestTraceFilter.REQUEST_ATTR);
        return traceId != null ? traceId : "unknown";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }
}

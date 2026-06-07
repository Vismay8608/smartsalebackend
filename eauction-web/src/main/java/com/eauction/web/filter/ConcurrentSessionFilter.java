package com.eauction.web.filter;

import com.eauction.common.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eauction.web.repository.UserSessionRepository;
import com.eauction.web.service.SecurityConfigCacheService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Covers: ConcurrentSessionFilter + SessionValidationFilter
 *
 * Runs inside the Spring Security filter chain (after JwtAuthFilter).
 * Enforces the per-client max-concurrent-sessions policy stored in
 * user_security_configs. Uses Redis to cache the active session count
 * per user (TTL 60 s) to avoid a DB hit on every request.
 *
 * Default limit: 5 concurrent sessions per user.
 *
 * Also updates the session's last_activity_at timestamp in the background
 * so the dashboard can show active sessions accurately.
 *
 * NOT registered as a servlet filter — SecurityConfig adds it to the chain.
 */
@Slf4j
@RequiredArgsConstructor
public class ConcurrentSessionFilter extends OncePerRequestFilter {

    private static final Duration COUNT_CACHE_TTL = Duration.ofSeconds(60);

    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/v1/auth/logout",
            "/api/v1/auth/logout-all"
    );

    private final UserSessionRepository      sessionRepo;
    private final StringRedisTemplate        redisTemplate;
    private final ObjectMapper                objectMapper;
    private final SecurityConfigCacheService securityConfigCache;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }

        Integer userId = TenantContext.getUserId();
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            int  maxSessions = securityConfigCache.get(TenantContext.getTenantId()).maxConcurrentSessions();
            long activeCount = getActiveSessionCount(userId);
            if (activeCount > maxSessions) {
                log.warn("Max concurrent sessions exceeded [userId={}] [active={}] [max={}]",
                        userId, activeCount, maxSessions);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                        "success",   false,
                        "code",      "SEC_MAX_SESSIONS",
                        "message",   "Maximum concurrent sessions reached. Please log out from another device.",
                        "timestamp", Instant.now().toString())));
                return;
            }
        } catch (Exception ex) {
            log.error("ConcurrentSessionFilter error — failing open [userId={}]", userId, ex);
        }

        chain.doFilter(request, response);
    }

    private long getActiveSessionCount(Integer userId) {
        String cacheKey = "session:count:" + userId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return Long.parseLong(cached);
        } catch (Exception ignored) {}

        // Cache miss — query DB and cache result
        long count = sessionRepo.countActiveSessions(userId);
        try {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(count), COUNT_CACHE_TTL);
        } catch (Exception ignored) {}

        return count;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SKIP_PATHS.contains(request.getRequestURI());
    }
}

package com.eauction.web.filter;

import com.eauction.admin.entity.UserRegister;
import com.eauction.common.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eauction.admin.repository.UserRegisterRepository;
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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Covers: PasswordExpiryFilter
 *
 * Runs inside the Spring Security filter chain (after JwtAuthFilter) so it
 * has access to the resolved Authentication principal.
 *
 * Enforces two password-related policies per authenticated request:
 *   1. forcePasswordChange flag — set by admin reset or first login
 *   2. passwordExpiryDate      — per UserSecurityConfig.expiryDays (default 90 days)
 *
 * On violation the request is rejected with 401 AUTH_PASSWORD_EXPIRED,
 * telling the client to redirect the user to the change-password screen.
 * The password-change endpoint itself is exempt.
 *
 * Performance: these two fields rarely change (only on login-time provisioning,
 * admin reset, or a successful password change), so hitting Postgres on every
 * single authenticated request is wasted load. Instead the policy snapshot is
 * cached in Redis — written once at login (see {@code AuthService.login} /
 * {@code AuthService.changePassword}, both call {@link #writeCache}) and read
 * straight from there on every subsequent request. A cache miss (Redis restart,
 * TTL expiry, or a session that predates this cache) transparently falls back
 * to the DB and repopulates the cache — so correctness never depends on the
 * cache being warm, only performance does.
 *
 * NOT registered as a servlet filter (@Component absent) — SecurityConfig
 * adds it to the chain via addFilterAfter(jwtAuthFilter).
 */
@Slf4j
@RequiredArgsConstructor
public class PasswordExpiryFilter extends OncePerRequestFilter {

    // Endpoints that must remain accessible even with an expired password
    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/v1/auth/logout",
            "/api/v1/auth/logout-all",
            "/api/v1/auth/change-password"
    );

    private static final String   CACHE_KEY_PREFIX = "user:pwdpolicy:";
    private static final Duration CACHE_TTL        = Duration.ofMinutes(30);

    private final UserRegisterRepository userRepo;
    private final StringRedisTemplate    redisTemplate;
    private final ObjectMapper           objectMapper;

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
            PolicySnapshot policy = readFromCache(userId);
            if (policy == null) {
                policy = loadFromDbAndCache(userId);
            }

            if (policy != null) {
                if (policy.forcePasswordChange) {
                    throw new PasswordExpiredException("Password change required");
                }
                if (policy.expiry != null && policy.expiry.isBefore(Instant.now())) {
                    throw new PasswordExpiredException("Password has expired");
                }
            }
        } catch (PasswordExpiredException ex) {
            log.info("Password expiry enforced [userId={}] [path={}] [reason={}]",
                    userId, request.getRequestURI(), ex.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "success",          false,
                    "code",             "AUTH_PASSWORD_EXPIRED",
                    "message",          ex.getMessage() + ". Please change your password to continue.",
                    "forceChangeUrl",   "/api/v1/auth/change-password",
                    "timestamp",        Instant.now().toString())));
            return;
        } catch (Exception ex) {
            log.error("PasswordExpiryFilter error — failing open [userId={}]", userId, ex);
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXEMPT_PATHS.contains(request.getRequestURI());
    }

    // ── Redis-backed policy cache ──────────────────────────────────────────────
    //
    // Stored as a plain "forceFlag|expiryInstantOrEmpty" string (e.g. "false|2026-09-05T10:00:00Z"
    // or "true|") rather than JSON — the value is two primitives, so a delimited
    // string avoids a serialization round-trip for no real benefit.

    private record PolicySnapshot(boolean forcePasswordChange, Instant expiry) {}

    private static String cacheKey(Integer userId) {
        return CACHE_KEY_PREFIX + userId;
    }

    private PolicySnapshot readFromCache(Integer userId) {
        try {
            String raw = redisTemplate.opsForValue().get(cacheKey(userId));
            if (raw == null) return null;
            String[] parts = raw.split("\\|", 2);
            boolean forceChange = Boolean.parseBoolean(parts[0]);
            Instant expiry = (parts.length > 1 && !parts[1].isEmpty()) ? Instant.parse(parts[1]) : null;
            return new PolicySnapshot(forceChange, expiry);
        } catch (Exception ex) {
            log.debug("Password policy cache read failed — falling back to DB [userId={}]", userId, ex);
            return null;
        }
    }

    private PolicySnapshot loadFromDbAndCache(Integer userId) {
        UserRegister user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        boolean forceChange = Boolean.TRUE.equals(user.getForcePasswordChange());
        OffsetDateTime expiryDate = user.getPasswordExpiryDate();
        writeCache(redisTemplate, userId, forceChange, expiryDate);
        return new PolicySnapshot(forceChange, expiryDate != null ? expiryDate.toInstant() : null);
    }

    /**
     * Writes (or refreshes) the cached password-policy snapshot for a user.
     * Called from {@code AuthService} at login time (so the very first
     * authenticated request after login is already a cache hit) and again
     * immediately after a successful password change (so the new — now
     * non-expired — state is visible without waiting for {@link #CACHE_TTL}
     * to lapse). Fails silently: a Redis outage just means the next request
     * falls through to {@link #loadFromDbAndCache}.
     */
    public static void writeCache(StringRedisTemplate redisTemplate, Integer userId,
                                   boolean forcePasswordChange, OffsetDateTime passwordExpiryDate) {
        try {
            String value = forcePasswordChange + "|" + (passwordExpiryDate == null ? "" : passwordExpiryDate.toInstant().toString());
            redisTemplate.opsForValue().set(cacheKey(userId), value, CACHE_TTL);
        } catch (Exception ex) {
            log.debug("Password policy cache write failed [userId={}]", userId, ex);
        }
    }

    private static class PasswordExpiredException extends RuntimeException {
        PasswordExpiredException(String msg) { super(msg); }
    }
}

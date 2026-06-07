package com.eauction.web.service;

import com.eauction.admin.entity.UserSecurityConfig;
import com.eauction.admin.repository.UserSecurityConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Read-through Redis cache for the per-client {@code user_security_configs} table.
 *
 * That table is consulted from several hot paths — every failed login
 * (lockout threshold + duration), every authenticated request that needs the
 * concurrent-session limit, and password changes (expiry-day policy) — yet its
 * values change only when an administrator edits a client's security policy
 * (rare). Querying Postgres on each of those checks is wasted load, and
 * {@link com.eauction.web.service.LoginAttemptService} was even issuing the
 * *same* lookup twice per failed attempt (once for the attempt limit, once for
 * the lockout duration).
 *
 * Snapshot is cached as a single delimited string under {@code security:config:{clientId}}
 * with a 1-hour TTL and refreshed transparently on miss. Call {@link #evict}
 * after an admin updates a client's config so the new values take effect
 * immediately rather than waiting out the TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityConfigCacheService {

    private static final String   CACHE_KEY_PREFIX = "security:config:";
    private static final Duration CACHE_TTL        = Duration.ofHours(1);

    private final UserSecurityConfigRepository repo;
    private final StringRedisTemplate          redisTemplate;

    public record ConfigSnapshot(int passwordExpiryDays,
                                 int maxFailedLoginAttempts,
                                 int accountLockoutDurationMinutes,
                                 int maxConcurrentSessions,
                                 int maxDevicesAllowed) {

        public static final ConfigSnapshot DEFAULTS = new ConfigSnapshot(90, 5, 30, 5, 3);
    }

    public ConfigSnapshot get(Integer clientId) {
        if (clientId == null) {
            return ConfigSnapshot.DEFAULTS;
        }

        ConfigSnapshot cached = readFromCache(clientId);
        if (cached != null) {
            return cached;
        }
        return loadFromDbAndCache(clientId);
    }

    /** Drop the cached snapshot so the next read re-fetches fresh values from the DB. */
    public void evict(Integer clientId) {
        if (clientId == null) return;
        try {
            redisTemplate.delete(cacheKey(clientId));
        } catch (Exception ex) {
            log.debug("Security config cache evict failed [clientId={}]", clientId, ex);
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private ConfigSnapshot readFromCache(Integer clientId) {
        try {
            String raw = redisTemplate.opsForValue().get(cacheKey(clientId));
            if (raw == null) return null;

            String[] parts = raw.split("\\|");
            if (parts.length != 5) return null;

            return new ConfigSnapshot(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]));
        } catch (Exception ex) {
            log.debug("Security config cache read failed — falling back to DB [clientId={}]", clientId, ex);
            return null;
        }
    }

    private ConfigSnapshot loadFromDbAndCache(Integer clientId) {
        ConfigSnapshot snapshot = repo.findByClientClientId(clientId)
                .map(this::toSnapshot)
                .orElse(ConfigSnapshot.DEFAULTS);

        try {
            String value = snapshot.passwordExpiryDays() + "|" + snapshot.maxFailedLoginAttempts() + "|"
                    + snapshot.accountLockoutDurationMinutes() + "|" + snapshot.maxConcurrentSessions()
                    + "|" + snapshot.maxDevicesAllowed();
            redisTemplate.opsForValue().set(cacheKey(clientId), value, CACHE_TTL);
        } catch (Exception ex) {
            log.debug("Security config cache write failed [clientId={}]", clientId, ex);
        }

        return snapshot;
    }

    private ConfigSnapshot toSnapshot(UserSecurityConfig config) {
        return new ConfigSnapshot(
                orDefault(config.getPasswordExpiryDays(),            ConfigSnapshot.DEFAULTS.passwordExpiryDays()),
                orDefault(config.getMaxFailedUserLoginAttempts(),    ConfigSnapshot.DEFAULTS.maxFailedLoginAttempts()),
                orDefault(config.getAccountLockoutDurationMinutes(), ConfigSnapshot.DEFAULTS.accountLockoutDurationMinutes()),
                orDefault(config.getMaxConcurrentSessions(),         ConfigSnapshot.DEFAULTS.maxConcurrentSessions()),
                orDefault(config.getMaxDevicesAllowed(),             ConfigSnapshot.DEFAULTS.maxDevicesAllowed()));
    }

    private static int orDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private static String cacheKey(Integer clientId) {
        return CACHE_KEY_PREFIX + clientId;
    }
}

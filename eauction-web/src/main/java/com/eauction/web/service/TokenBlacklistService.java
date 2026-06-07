package com.eauction.web.service;

import com.eauction.web.entity.UserTokenBlacklist;
import com.eauction.web.repository.UserTokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String REDIS_PREFIX = "jti:blacklist:";

    private final UserTokenBlacklistRepository blacklistRepository;
    private final StringRedisTemplate          redisTemplate;

    /**
     * Blacklists a JWT by its JTI.
     * Redis is primary (fast); DB is durable fallback.
     */
    @Transactional
    public void blacklist(UUID jti, Integer userId, String sessionId,
                          String reason, Date originalExpiry, Integer revokedBy) {
        // Redis — set TTL to original token expiry
        long ttlSeconds = Math.max(0, originalExpiry.toInstant().getEpochSecond() - Instant.now().getEpochSecond());
        redisTemplate.opsForValue().set(REDIS_PREFIX + jti, reason, Duration.ofSeconds(ttlSeconds));

        // DB fallback
        if (!blacklistRepository.existsByJti(jti)) {
            blacklistRepository.save(UserTokenBlacklist.builder()
                    .jti(jti)
                    .userId(userId)
                    .sessionId(sessionId)
                    .revokeReason(reason)
                    .originalExpiresAt(OffsetDateTime.ofInstant(originalExpiry.toInstant(),
                            java.time.ZoneOffset.UTC))
                    .revokedBy(revokedBy)
                    .build());
        }
    }

    /** Returns true if the JTI is blacklisted (checks Redis first, then DB). */
    public boolean isBlacklisted(UUID jti) {
        // Redis check (fast path)
        Boolean inRedis = redisTemplate.hasKey(REDIS_PREFIX + jti);
        if (Boolean.TRUE.equals(inRedis)) return true;

        // DB fallback (handles Redis eviction)
        return blacklistRepository.existsByJti(jti);
    }
}

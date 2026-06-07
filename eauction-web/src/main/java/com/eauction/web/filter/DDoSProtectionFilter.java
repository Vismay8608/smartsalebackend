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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Covers: DDoSProtectionFilter
 *
 * Goes beyond simple per-minute rate limiting (RateLimitFilter) by detecting
 * *abuse patterns* and progressively escalating the response:
 *
 *   1. Burst velocity  — more than {@link #BURST_LIMIT} requests/second from
 *      one IP triggers a soft 429 throttle and increments a violation counter.
 *   2. Repeat offender — {@link #VIOLATION_LIMIT} bursts within a 5-minute
 *      window auto-bans the IP for {@link #AUTOBAN_DURATION}.
 *   3. Error storms    — {@link #ERROR_LIMIT} client/server error responses
 *      (4xx/5xx — typical of credential-stuffing / scanning / fuzzing tools
 *      such as Burp Intruder) within 60 s also auto-bans the IP.
 *
 * Auto-bans are stored as their own short-lived Redis keys (NOT written into
 * the permanent security:ip:blacklist SET managed by IpBlacklistFilter) so
 * they expire automatically without manual cleanup, while a genuine manual
 * blacklist entry remains until explicitly removed.
 *
 * Fails OPEN if Redis is unavailable.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
@RequiredArgsConstructor
public class DDoSProtectionFilter extends OncePerRequestFilter {

    private static final int BURST_LIMIT      = 25;   // requests / second / IP
    private static final int VIOLATION_LIMIT  = 5;    // bursts within VIOLATION_WINDOW → autoban
    private static final int ERROR_LIMIT      = 30;   // 4xx/5xx responses within ERROR_WINDOW → autoban

    private static final Duration BURST_WINDOW     = Duration.ofSeconds(2);
    private static final Duration VIOLATION_WINDOW = Duration.ofMinutes(5);
    private static final Duration ERROR_WINDOW     = Duration.ofMinutes(1);
    private static final Duration AUTOBAN_DURATION = Duration.ofHours(1);

    private static final String AUTOBAN_PREFIX   = "ddos:autoban:";
    private static final String BURST_PREFIX     = "ddos:burst:";
    private static final String VIOLATION_PREFIX = "ddos:violations:";
    private static final String ERROR_PREFIX     = "ddos:errors:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String ip = IpBlacklistFilter.resolveIp(request);

        try {
            Long autobanTtl = redisTemplate.getExpire(AUTOBAN_PREFIX + ip);
            if (autobanTtl != null && autobanTtl > 0) {
                log.warn("Blocked auto-banned IP [ip={}] [path={}] [ttl={}s] [traceId={}]",
                        ip, request.getRequestURI(), autobanTtl, traceId(request));
                sendThrottle(response, HttpStatus.FORBIDDEN, "SEC_DDOS_BANNED",
                        "Access temporarily blocked due to abusive traffic patterns", autobanTtl);
                return;
            }

            if (checkBurstAndViolations(ip, request)) {
                sendThrottle(response, HttpStatus.TOO_MANY_REQUESTS, "SEC_DDOS_BURST",
                        "Request rate too high — slow down", BURST_WINDOW.toSeconds());
                return;
            }
        } catch (Exception ex) {
            log.error("DDoS pre-check failed — failing open [ip={}]", ip, ex);
        }

        chain.doFilter(request, response);

        try {
            recordErrorIfApplicable(ip, response, request);
        } catch (Exception ex) {
            log.error("DDoS post-check failed [ip={}]", ip, ex);
        }
    }

    /** @return true if this request must be throttled (burst exceeded). */
    private boolean checkBurstAndViolations(String ip, HttpServletRequest request) {
        long second   = Instant.now().getEpochSecond();
        String key    = BURST_PREFIX + ip + ":" + second;
        Long   count  = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, BURST_WINDOW);
        }
        if (count == null || count <= BURST_LIMIT) {
            return false;
        }

        String violationKey = VIOLATION_PREFIX + ip;
        Long   violations   = redisTemplate.opsForValue().increment(violationKey);
        if (violations != null && violations == 1L) {
            redisTemplate.expire(violationKey, VIOLATION_WINDOW);
        }
        log.warn("Burst limit exceeded [ip={}] [path={}] [count={}] [violations={}] [traceId={}]",
                ip, request.getRequestURI(), count, violations, traceId(request));

        if (violations != null && violations >= VIOLATION_LIMIT) {
            autoBan(ip, "repeated burst violations (" + violations + " in " + VIOLATION_WINDOW.toMinutes() + "m)");
        }
        return true;
    }

    private void recordErrorIfApplicable(String ip, HttpServletResponse response, HttpServletRequest request) {
        int status = response.getStatus();
        if (status < 400) {
            return;
        }
        long minute = Instant.now().getEpochSecond() / 60;
        String key  = ERROR_PREFIX + ip + ":" + minute;
        Long   count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ERROR_WINDOW);
        }
        if (count != null && count >= ERROR_LIMIT) {
            log.warn("Error storm detected [ip={}] [path={}] [errorsInWindow={}] [traceId={}]",
                    ip, request.getRequestURI(), count, traceId(request));
            autoBan(ip, "error storm (" + count + " 4xx/5xx responses in " + ERROR_WINDOW.toSeconds() + "s — possible scanning/fuzzing)");
        }
    }

    private void autoBan(String ip, String reason) {
        String key = AUTOBAN_PREFIX + ip;
        Boolean alreadyBanned = redisTemplate.hasKey(key);
        redisTemplate.opsForValue().set(key, reason, AUTOBAN_DURATION);
        if (!Boolean.TRUE.equals(alreadyBanned)) {
            log.error("AUTO-BAN applied [ip={}] [duration={}m] [reason={}]",
                    ip, AUTOBAN_DURATION.toMinutes(), reason);
        }
    }

    private void sendThrottle(HttpServletResponse response, HttpStatus status, String code,
                              String message, long retryAfterSeconds) throws IOException {
        response.setStatus(status.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success",    false,
                "code",       code,
                "message",    message,
                "retryAfter", retryAfterSeconds,
                "timestamp",  Instant.now().toString())));
    }

    private Object traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(RequestTraceFilter.REQUEST_ATTR);
        return traceId != null ? traceId : "unknown";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.startsWith("/actuator/health");
    }
}

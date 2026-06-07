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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Covers: ReplayAttackProtectionFilter
 *
 * Distinct from {@link IdempotencyFilter} (which is a *client convenience* —
 * "if I retry, give me the same result"). This filter is a *server-side
 * defense* — "a captured request must never be processable twice", which
 * matters when an attacker has sniffed/proxied a legitimate signed request
 * (e.g. via a MITM proxy or a Burp Suite repeater) and resends it verbatim.
 *
 * Only enforced for requests that opt into the signed-API contract (i.e.
 * those that present {@code X-Api-Key-Id} — see {@link ApiSignatureValidationFilter}).
 * Plain browser/JWT requests are NOT required to send these headers — a
 * browser cannot hold a shared secret securely, so replay defense for that
 * channel instead relies on short-lived JWTs + per-action Idempotency-Key.
 *
 * Required headers for signed calls:
 *   X-Request-Timestamp : epoch milliseconds, must be within ±5 minutes of server time
 *   X-Request-Nonce     : unique opaque token (UUID recommended), ≤128 chars,
 *                         single-use — tracked in Redis for {@link #NONCE_TTL}
 *
 * Runs AFTER {@link ApiSignatureValidationFilter} so the nonce is only
 * "burned" once the request has been proven authentic (an attacker cannot
 * exhaust a victim's nonce space by sending unsigned junk).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 14)
@RequiredArgsConstructor
public class ReplayAttackProtectionFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY   = ApiSignatureValidationFilter.HEADER_KEY_ID;
    public static final String HEADER_TIMESTAMP = "X-Request-Timestamp";
    public static final String HEADER_NONCE     = "X-Request-Nonce";

    private static final Duration CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(5);
    private static final Duration NONCE_TTL            = Duration.ofMinutes(10);
    private static final int      MAX_NONCE_LENGTH     = 128;

    private static final String NONCE_KEY_PREFIX = "replay:nonce:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String apiKeyId = request.getHeader(HEADER_API_KEY);
        if (apiKeyId == null || apiKeyId.isBlank()) {
            // Not a signed-API call — replay defense for this channel is
            // handled by short JWT TTL + Idempotency-Key.
            chain.doFilter(request, response);
            return;
        }

        String tsHeader = request.getHeader(HEADER_TIMESTAMP);
        String nonce    = request.getHeader(HEADER_NONCE);

        if (tsHeader == null || tsHeader.isBlank() || nonce == null || nonce.isBlank()) {
            sendError(response, HttpStatus.BAD_REQUEST, "SEC_REPLAY_HEADERS_REQUIRED",
                    "Signed requests must include " + HEADER_TIMESTAMP + " and " + HEADER_NONCE + " headers");
            return;
        }
        if (nonce.length() > MAX_NONCE_LENGTH) {
            sendError(response, HttpStatus.BAD_REQUEST, "SEC_REPLAY_NONCE_INVALID",
                    HEADER_NONCE + " must not exceed " + MAX_NONCE_LENGTH + " characters");
            return;
        }

        long requestEpochMillis;
        try {
            requestEpochMillis = Long.parseLong(tsHeader.trim());
        } catch (NumberFormatException ex) {
            sendError(response, HttpStatus.BAD_REQUEST, "SEC_REPLAY_TIMESTAMP_INVALID",
                    HEADER_TIMESTAMP + " must be epoch milliseconds");
            return;
        }

        Instant requestTime = Instant.ofEpochMilli(requestEpochMillis);
        Duration drift = Duration.between(requestTime, Instant.now()).abs();
        if (drift.compareTo(CLOCK_SKEW_TOLERANCE) > 0) {
            log.warn("Replay timestamp out of tolerance [apiKey={}] [path={}] [driftSec={}] [traceId={}]",
                    apiKeyId, request.getRequestURI(), drift.getSeconds(), traceId(request));
            sendError(response, HttpStatus.BAD_REQUEST, "SEC_REPLAY_TIMESTAMP_EXPIRED",
                    "Request timestamp is outside the allowed " + CLOCK_SKEW_TOLERANCE.toMinutes() + "-minute window");
            return;
        }

        String nonceKey = NONCE_KEY_PREFIX + apiKeyId + ":" + nonce;
        try {
            Boolean firstUse = redisTemplate.opsForValue().setIfAbsent(nonceKey, request.getRequestURI(), NONCE_TTL);
            if (!Boolean.TRUE.equals(firstUse)) {
                log.error("REPLAY DETECTED — nonce reuse [apiKey={}] [nonce={}] [path={}] [traceId={}]",
                        apiKeyId, nonce, request.getRequestURI(), traceId(request));
                sendError(response, HttpStatus.CONFLICT, "SEC_REPLAY_DETECTED",
                        "This request has already been processed (nonce reuse detected)");
                return;
            }
        } catch (Exception ex) {
            log.error("Replay nonce check failed — failing open [apiKey={}]", apiKeyId, ex);
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
}

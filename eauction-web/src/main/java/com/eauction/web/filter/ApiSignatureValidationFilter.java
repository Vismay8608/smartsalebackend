package com.eauction.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Covers: ApiSignatureValidationFilter
 *
 * HMAC-SHA256 request signing — the same family of mechanism used by AWS
 * SigV4, Stripe and Razorpay webhooks. Provides end-to-end *integrity and
 * authenticity* for server-to-server / partner / admin-tool calls: even if a
 * request is intercepted and replayed through a proxy such as Burp Suite, any
 * modification to the method, path, query, timestamp, nonce or body
 * invalidates the signature.
 *
 * Deliberately OPT-IN — only enforced when the caller presents
 * {@code X-Api-Key-Id}. Browser SPA traffic authenticates via short-lived JWT
 * and is NOT required to sign requests (a browser cannot hold an HMAC secret
 * without exposing it in the JS bundle — see suggestions in PR notes for the
 * browser-side equivalents: TLS, CSP, SRI, short-lived tokens).
 *
 * ── Client-side recipe ────────────────────────────────────────────────────
 *   timestamp = currentEpochMillis()
 *   nonce     = randomUUID()
 *   bodyHash  = hex(sha512_256(rawRequestBodyBytes))  // sha512_256("") for no body
 *   canonical = METHOD + "\n" + PATH + "\n" + RAW_QUERY_STRING + "\n"
 *               + timestamp + "\n" + nonce + "\n" + bodyHash
 *   signature = base64(hmacSHA256(canonical, apiSecret))
 *
 *   Headers to send:
 *     X-Api-Key-Id        : <keyId>
 *     X-Request-Timestamp : <timestamp>
 *     X-Request-Nonce     : <nonce>
 *     X-Api-Signature     : <signature>
 * ──────────────────────────────────────────────────────────────────────────
 *
 * Secrets are provisioned out-of-band and stored in Redis as plain UTF-8
 * strings (never logged, never echoed):
 *     SET apikey:secret:partner-acme-01  "k8Jp2m...64-char-random-secret"
 *
 * Runs BEFORE {@link ReplayAttackProtectionFilter} so a nonce is only ever
 * "burned" for a request that is provably authentic.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 13)
@RequiredArgsConstructor
public class ApiSignatureValidationFilter extends OncePerRequestFilter {

    public static final String HEADER_KEY_ID    = "X-Api-Key-Id";
    public static final String HEADER_TIMESTAMP = "X-Request-Timestamp";
    public static final String HEADER_NONCE     = "X-Request-Nonce";
    public static final String HEADER_SIGNATURE = "X-Api-Signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SECRET_KEY_PREFIX = "apikey:secret:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        String keyId = request.getHeader(HEADER_KEY_ID);
        if (keyId == null || keyId.isBlank()) {
            // Not a signed call — JWT bearer auth handles this request.
            chain.doFilter(request, response);
            return;
        }

        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String nonce     = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            sendError(response, HttpStatus.UNAUTHORIZED, "SEC_SIGNATURE_HEADERS_REQUIRED",
                    "Signed requests require " + HEADER_TIMESTAMP + ", " + HEADER_NONCE
                            + " and " + HEADER_SIGNATURE + " headers");
            return;
        }

        String secret;
        try {
            secret = redisTemplate.opsForValue().get(SECRET_KEY_PREFIX + keyId);
        } catch (Exception ex) {
            log.error("API signature secret lookup failed — failing closed [keyId={}]", keyId, ex);
            sendError(response, HttpStatus.SERVICE_UNAVAILABLE, "SEC_SIGNATURE_UNAVAILABLE",
                    "Signature verification temporarily unavailable");
            return;
        }
        if (secret == null || secret.isBlank()) {
            log.warn("Unknown API key presented [keyId={}] [path={}] [ip={}] [traceId={}]",
                    keyId, request.getRequestURI(), IpBlacklistFilter.resolveIp(request), traceId(request));
            sendError(response, HttpStatus.UNAUTHORIZED, "SEC_SIGNATURE_UNKNOWN_KEY", "Unknown API key");
            return;
        }

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String canonical = buildCanonicalString(request, timestamp, nonce, bodyBytes);

        String expectedSignature;
        try {
            expectedSignature = sign(canonical, secret);
        } catch (Exception ex) {
            log.error("HMAC computation failed [keyId={}]", keyId, ex);
            sendError(response, HttpStatus.INTERNAL_SERVER_ERROR, "SEC_SIGNATURE_ERROR",
                    "Signature verification failed");
            return;
        }

        if (!constantTimeEquals(expectedSignature, signature.trim())) {
            log.error("SIGNATURE MISMATCH — possible tampering [keyId={}] [path={}] [method={}] [ip={}] [traceId={}]",
                    keyId, request.getRequestURI(), request.getMethod(),
                    IpBlacklistFilter.resolveIp(request), traceId(request));
            sendError(response, HttpStatus.UNAUTHORIZED, "SEC_SIGNATURE_INVALID",
                    "Request signature verification failed — request may have been tampered with");
            return;
        }

        log.debug("API signature verified [keyId={}] [path={}]", keyId, request.getRequestURI());
        chain.doFilter(new CachedBodyHttpServletRequest(request, bodyBytes), response);
    }

    // ── Canonical request construction & signing ──────────────────────────────

    private String buildCanonicalString(HttpServletRequest request, String timestamp,
                                         String nonce, byte[] bodyBytes) {
        String query = request.getQueryString();
        return request.getMethod().toUpperCase() + '\n'
                + request.getRequestURI() + '\n'
                + (query == null ? "" : query) + '\n'
                + timestamp + '\n'
                + nonce + '\n'
                + sha512_256Hex(bodyBytes);
    }

    private String sha512_256Hex(byte[] data) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-512/256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-512/256 not available", ex);
        }
    }

    private String sign(String canonical, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(raw);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
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
        // Only POST/PUT/PATCH/DELETE bodies are meaningfully signable; signed
        // GETs are still verified above (empty-body hash) when the key-id header
        // is present — shouldNotFilter only short-circuits truly exempt paths.
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    /**
     * Re-readable request wrapper — required because signature verification
     * must consume the body BEFORE Spring MVC / Jackson reads it downstream.
     */
    static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return byteStream.available() == 0; }
                @Override public boolean isReady()    { return true; }
                @Override public void setReadListener(ReadListener readListener) { /* not needed for sync processing */ }
                @Override public int read() { return byteStream.read(); }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}

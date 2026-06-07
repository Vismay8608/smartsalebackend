package com.eauction.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Covers: RequestLoggingFilter + AuditLoggingFilter + ActivityTrackingFilter
 *         + SensitiveDataMaskingFilter
 *
 * Wraps every request/response with ContentCaching wrappers so the body can
 * be read for logging AFTER the chain completes without interfering with
 * downstream body consumption. Sensitive field values are masked before logging.
 *
 * Writes structured JSON to a dedicated AUDIT logger (wire to a separate
 * log file / SIEM sink in prod via logback.xml).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private static final int MAX_BODY_CHARS = 2048;

    // Mask sensitive JSON field values — key stays visible, value becomes ***
    private static final Pattern MASK_PATTERN = Pattern.compile(
            "(?i)\"(password|passwordHash|password_hash|token|accessToken|refreshToken|" +
            "secret|mfaSecret|mfa_secret|pin|otp|cvv|cardNumber|card_number|" +
            "encryptedData|privateKey|privatekey)\"\\s*:\\s*\"[^\"]*\"");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        var wrappedReq  = new ContentCachingRequestWrapper(request,  MAX_BODY_CHARS);
        var wrappedResp = new ContentCachingResponseWrapper(response);
        long startMs    = System.currentTimeMillis();

        try {
            chain.doFilter(wrappedReq, wrappedResp);
        } finally {
            writeAuditLog(wrappedReq, wrappedResp, System.currentTimeMillis() - startMs);
            wrappedResp.copyBodyToResponse();
        }
    }

    private void writeAuditLog(ContentCachingRequestWrapper  req,
                               ContentCachingResponseWrapper resp,
                               long durationMs) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("traceId",      req.getAttribute(RequestTraceFilter.REQUEST_ATTR));
            entry.put("ts",           Instant.now().toString());
            entry.put("method",       req.getMethod());
            entry.put("path",         req.getRequestURI());
            entry.put("query",        req.getQueryString());
            entry.put("status",       resp.getStatus());
            entry.put("durationMs",   durationMs);
            entry.put("ip",           IpBlacklistFilter.resolveIp(req));
            entry.put("userAgent",    req.getHeader("User-Agent"));
            entry.put("respBytes",    resp.getContentSize());

            String ct = req.getContentType();
            if (ct != null && ct.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                byte[] body = req.getContentAsByteArray();
                if (body.length > 0) {
                    entry.put("requestBody", masked(truncate(new String(body, StandardCharsets.UTF_8))));
                }
            }

            AUDIT.info(objectMapper.writeValueAsString(entry));
        } catch (Exception ex) {
            log.error("Audit log write failed [traceId={}]",
                    req.getAttribute(RequestTraceFilter.REQUEST_ATTR), ex);
        }
    }

    private String masked(String json) {
        return MASK_PATTERN.matcher(json)
                .replaceAll(m -> "\"" + m.group(1) + "\":\"***\"");
    }

    private String truncate(String s) {
        return s.length() > MAX_BODY_CHARS
                ? s.substring(0, MAX_BODY_CHARS) + "…[truncated]"
                : s;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}

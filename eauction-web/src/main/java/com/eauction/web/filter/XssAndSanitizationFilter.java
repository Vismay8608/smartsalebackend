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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Covers: XSSProtectionFilter + SQLInjectionProtectionFilter
 *         + RequestSanitizationFilter + InputValidationFilter
 *
 * Scans query parameters and selected headers for known XSS and SQL injection
 * patterns. JSON request bodies are NOT scanned here — the Jackson deserialiser
 * + Bean Validation annotations handle that layer. JPA parameterised queries
 * eliminate SQL injection risk in the data layer.
 *
 * Rejects with 400 on detection rather than silently stripping, so the caller
 * sees a clear error rather than unexpected behaviour from mangled input.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 7)
@RequiredArgsConstructor
public class XssAndSanitizationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // XSS patterns — script tags, event handlers, javascript: URIs, data: URIs
    private static final List<Pattern> XSS_PATTERNS = List.of(
            Pattern.compile("<\\s*script[^>]*>",               Pattern.CASE_INSENSITIVE),
            Pattern.compile("</\\s*script\\s*>",               Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript\\s*:",                  Pattern.CASE_INSENSITIVE),
            Pattern.compile("data\\s*:\\s*text/html",           Pattern.CASE_INSENSITIVE),
            Pattern.compile("on\\w+\\s*=",                     Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\s*iframe[^>]*>",               Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\s*\\(",               Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript\\s*:",                   Pattern.CASE_INSENSITIVE)
    );

    // SQL injection patterns — common injection entry points in URL params
    private static final List<Pattern> SQLI_PATTERNS = List.of(
            Pattern.compile("('\\s*(or|and)\\s*'?\\d)|(-{2})|(/\\*.*\\*/)",
                            Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(union\\s+select|drop\\s+table|insert\\s+into|" +
                            "delete\\s+from|exec\\s*\\(|execute\\s*\\(|" +
                            "xp_cmdshell|information_schema)\\b",
                            Pattern.CASE_INSENSITIVE),
            Pattern.compile("';\\s*\\w",                       Pattern.CASE_INSENSITIVE)
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        // Scan query parameters
        for (Map.Entry<String, String[]> param : request.getParameterMap().entrySet()) {
            for (String value : param.getValue()) {
                if (containsThreat(value)) {
                    log.warn("Malicious input detected in query param [param={}] [path={}] [ip={}] [traceId={}]",
                            param.getKey(), request.getRequestURI(),
                            IpBlacklistFilter.resolveIp(request),
                            request.getAttribute(RequestTraceFilter.REQUEST_ATTR));
                    sendError(response);
                    return;
                }
            }
        }

        // Scan the User-Agent header (common injection vector via automated tools)
        String ua = request.getHeader("User-Agent");
        if (ua != null && containsThreat(ua)) {
            log.warn("Malicious User-Agent detected [path={}] [ip={}]",
                    request.getRequestURI(), IpBlacklistFilter.resolveIp(request));
            sendError(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean containsThreat(String value) {
        for (Pattern p : XSS_PATTERNS)  { if (p.matcher(value).find()) return true; }
        for (Pattern p : SQLI_PATTERNS) { if (p.matcher(value).find()) return true; }
        return false;
    }

    private void sendError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success",   false,
                "code",      "REQ003",
                "message",   "Request contains invalid or potentially malicious content",
                "timestamp", Instant.now().toString())));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || request.getRequestURI().startsWith("/actuator/");
    }
}

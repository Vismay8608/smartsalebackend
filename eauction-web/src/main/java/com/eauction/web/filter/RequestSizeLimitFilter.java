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
import java.util.Map;

/**
 * Covers: RequestSizeValidationFilter
 *
 * Rejects requests whose Content-Length exceeds 1 MB before any body parsing
 * occurs. Prevents memory exhaustion and large-payload DoS attacks.
 * File-upload endpoints (multipart) are exempt — their size is enforced at
 * the application layer via spring.servlet.multipart.max-file-size.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@RequiredArgsConstructor
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final long MAX_BYTES = 1_048_576L; // 1 MB

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        long length = request.getContentLengthLong();
        if (length > MAX_BYTES) {
            log.warn("Payload too large [size={}] [limit={}] [path={}] [traceId={}]",
                    length, MAX_BYTES, request.getRequestURI(),
                    request.getAttribute(RequestTraceFilter.REQUEST_ATTR));
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "success",   false,
                    "code",      "REQ002",
                    "message",   "Request body must not exceed 1 MB",
                    "timestamp", Instant.now().toString())));
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ct = request.getContentType();
        return HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.DELETE.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || (ct != null && ct.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE))
                || request.getRequestURI().startsWith("/actuator/");
    }
}

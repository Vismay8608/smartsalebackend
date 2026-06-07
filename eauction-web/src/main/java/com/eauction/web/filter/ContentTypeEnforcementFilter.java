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
import java.util.Set;

/**
 * Covers: ContentTypeValidationFilter
 *
 * Enforces that all state-changing requests (POST / PUT / PATCH) declare
 * Content-Type: application/json (or multipart/form-data for uploads).
 * Rejects early with 415 before the body is read or business logic runs.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RequiredArgsConstructor
public class ContentTypeEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        if (MUTATING_METHODS.contains(request.getMethod().toUpperCase())) {
            String ct = request.getContentType();
            boolean valid = ct != null
                    && (ct.startsWith(MediaType.APPLICATION_JSON_VALUE)
                        || ct.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE));

            if (!valid) {
                log.debug("Rejected bad Content-Type [method={}] [ct={}] [path={}]",
                        request.getMethod(), ct, request.getRequestURI());
                response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                        "success",   false,
                        "code",      "REQ001",
                        "message",   "Content-Type must be application/json",
                        "timestamp", Instant.now().toString())));
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}

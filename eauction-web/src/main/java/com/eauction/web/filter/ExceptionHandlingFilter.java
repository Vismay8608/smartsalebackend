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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Covers: ExceptionHandlingFilter
 *
 * Last-resort safety net for exceptions that escape the filter chain (and
 * therefore would otherwise reach the container's default error page —
 * potentially leaking stack traces, server banners or HTML error pages to
 * the client). @ControllerAdvice handles exceptions thrown by controllers;
 * this filter handles exceptions thrown by *filters* and the dispatcher
 * itself, guaranteeing every response — even a failure — is the platform's
 * standard JSON envelope.
 *
 * Always responds with the standard {success,code,message,traceId,timestamp}
 * shape and NEVER includes exception messages, class names or stack traces
 * in the body (only in the server-side log).
 *
 * Placed early (Order +5) so it wraps the bulk of the security chain,
 * authentication, authorization and the DispatcherServlet/controllers —
 * everything except the handful of outer filters that already fail open
 * internally (trace, logging, headers, content-type, size-limit).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class ExceptionHandlingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        try {
            chain.doFilter(request, response);
        } catch (Exception ex) {
            handle(request, response, ex);
        }
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, Exception ex)
            throws IOException {

        if (response.isCommitted()) {
            log.error("Unhandled exception after response commit [path={}] [traceId={}]",
                    request.getRequestURI(), traceId(request), ex);
            return;
        }

        HttpStatus status;
        String     code;
        String     message;

        String simpleName = ex.getClass().getSimpleName();
        switch (simpleName) {
            case "AccessDeniedException" -> {
                status = HttpStatus.FORBIDDEN;
                code = "AUTHZ001";
                message = "Access denied";
            }
            case "AuthenticationException", "BadCredentialsException", "InsufficientAuthenticationException" -> {
                status = HttpStatus.UNAUTHORIZED;
                code = "AUTH005";
                message = "Authentication required";
            }
            case "MaxUploadSizeExceededException" -> {
                status = HttpStatus.PAYLOAD_TOO_LARGE;
                code = "REQ413";
                message = "Uploaded file exceeds the maximum allowed size";
            }
            case "MultipartException" -> {
                status = HttpStatus.BAD_REQUEST;
                code = "REQ400";
                message = "Malformed multipart request";
            }
            case "IllegalArgumentException", "MethodArgumentTypeMismatchException", "MissingServletRequestParameterException" -> {
                status = HttpStatus.BAD_REQUEST;
                code = "REQ400";
                message = "Invalid request parameters";
            }
            case "ClientAbortException", "AsyncRequestNotUsableException" -> {
                // Client disconnected mid-response — nothing useful to send back
                log.debug("Client aborted request [path={}] [traceId={}]", request.getRequestURI(), traceId(request));
                return;
            }
            default -> {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                code = "SYS500";
                message = "An unexpected error occurred. Please try again later.";
            }
        }

        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Unhandled exception [path={}] [traceId={}] [type={}]",
                    request.getRequestURI(), traceId(request), simpleName, ex);
        } else {
            log.warn("Handled exception [path={}] [traceId={}] [type={}] [msg={}]",
                    request.getRequestURI(), traceId(request), simpleName, ex.getMessage());
        }

        response.reset();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success",   false,
                "code",      code,
                "message",   message,
                "traceId",   String.valueOf(traceId(request)),
                "timestamp", Instant.now().toString())));
    }

    private Object traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(RequestTraceFilter.REQUEST_ATTR);
        return traceId != null ? traceId : "unknown";
    }
}

package com.eauction.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Writes defensive HTTP response headers on every non-OPTIONS request.
 * This is a pure REST/JSON API — no browser rendering — so CSP is locked
 * to default-src 'none' and Swagger is allowed only in non-prod profiles.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        // Clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // MIME sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Legacy browser XSS filter
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // HSTS — enforce HTTPS for 1 year, include subdomains
        response.setHeader("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");

        // CSP — API-only: no scripts, no frames, no resources from other origins
        response.setHeader("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'");

        // Don't leak referrer across origins
        response.setHeader("Referrer-Policy", "no-referrer");

        // Disable browser features not needed by a fintech API
        response.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=(), usb=()");

        // No caching of API responses
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // Remove identifying server headers
        response.setHeader("Server", "");

        chain.doFilter(request, response);
    }

    // CORS preflight — browser needs to handle it without extra headers interfering
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }
}

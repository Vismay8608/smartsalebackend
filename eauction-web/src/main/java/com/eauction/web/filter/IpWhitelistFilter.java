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
import java.time.Instant;
import java.util.Map;

/**
 * Covers: IPWhitelistFilter
 *
 * Optional companion to IpBlacklistFilter. When the whitelist is active only
 * IPs explicitly listed are allowed through; everything else is blocked.
 * Useful for locking admin endpoints to office/VPN CIDR ranges.
 *
 * Enable whitelist:   SET security:ip:whitelist:active "true"
 * Add IP to list:     SADD security:ip:whitelist 10.0.0.5
 * Disable whitelist:  DEL security:ip:whitelist:active
 *
 * Whitelist is checked BEFORE the blacklist so an explicitly whitelisted IP
 * can never be accidentally blocked by the blacklist.
 *
 * Fails OPEN if Redis is unavailable.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 9)
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {

    static final String KEY_ACTIVE    = "security:ip:whitelist:active";
    static final String KEY_WHITELIST = "security:ip:whitelist";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        try {
            String active = redisTemplate.opsForValue().get(KEY_ACTIVE);
            if ("true".equalsIgnoreCase(active)) {
                String ip       = IpBlacklistFilter.resolveIp(request);
                Boolean allowed = redisTemplate.opsForSet().isMember(KEY_WHITELIST, ip);
                if (!Boolean.TRUE.equals(allowed)) {
                    log.warn("IP not in whitelist [ip={}] [path={}] [traceId={}]",
                            ip, request.getRequestURI(),
                            request.getAttribute(RequestTraceFilter.REQUEST_ATTR));
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                            "success",   false,
                            "code",      "SEC003",
                            "message",   "Access denied",
                            "timestamp", Instant.now().toString())));
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("IP whitelist Redis check failed — failing open", ex);
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || request.getRequestURI().startsWith("/actuator/health");
    }
}

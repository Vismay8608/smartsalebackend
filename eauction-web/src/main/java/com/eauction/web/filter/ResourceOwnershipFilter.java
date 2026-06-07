package com.eauction.web.filter;

import com.eauction.common.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Covers: DataAccessControlFilter + "URL / path-variable tampering" protection
 *
 * Defends against IDOR (Insecure Direct Object Reference) — the classic
 * Burp-Suite attack where an authenticated user simply edits the numeric ID
 * in the URL (e.g. changes /onboarding/sellers/company/12/profile to /.../13/profile)
 * to read or modify another tenant's data.
 *
 * For every inbound request whose path contains an "ownership" path variable
 * (userId, clientId, …) this filter compares that path value against the
 * matching claim baked into the caller's signed JWT (TenantContext, populated
 * by JwtAuthFilter from verified token claims — NOT client-suppliable input).
 * A mismatch means the URL was edited to point at someone else's resource and
 * is rejected with 403 before it ever reaches the controller/service layer.
 *
 * Platform-level SYSTEM users are exempt (their job is precisely to manage
 * other tenants' data) — fine-grained checks for them are enforced instead by
 * @PreAuthorize / permission checks at the service layer.
 *
 * This is a *defense-in-depth* layer — it does not replace per-resource
 * authorization in the service layer (e.g. verifying a SELLER company admin
 * may only manage branches that belong to their own clientId), it simply
 * guarantees a tampered URL can never reach that layer with a mismatched
 * tenant/user ID baked into the signed token.
 *
 * Runs inside the Spring Security chain (after JwtAuthFilter) — NOT
 * registered as a servlet filter (@Component absent); SecurityConfig wires
 * it in via addFilterAfter(...).
 */
@Slf4j
@RequiredArgsConstructor
public class ResourceOwnershipFilter extends OncePerRequestFilter {

    private enum Claim { USER_ID, CLIENT_ID }

    private record OwnershipRule(String pattern, String variable, Claim claim) {}

    /**
     * Path templates containing exactly one "ownership" variable, mapped to
     * the JWT claim that variable must equal. Order matters — first match wins.
     * Add a new rule here whenever a controller introduces a new
     * /{userId}/ or /{clientId}/ style path variable.
     */
    private static final List<OwnershipRule> RULES = List.of(
            new OwnershipRule("/api/v1/admin/system/users/{userId}",     "userId",   Claim.USER_ID),
            new OwnershipRule("/api/v1/admin/system/users/{userId}/**",  "userId",   Claim.USER_ID),
            new OwnershipRule("/api/v1/onboarding/sellers/company/{clientId}/**", "clientId", Claim.CLIENT_ID),
            new OwnershipRule("/api/v1/onboarding/sellers/{clientId}/status",     "clientId", Claim.CLIENT_ID),
            new OwnershipRule("/api/v1/onboarding/buyers/company/{clientId}/**",  "clientId", Claim.CLIENT_ID),
            new OwnershipRule("/api/v1/onboarding/buyers/{clientId}/status",      "clientId", Claim.CLIENT_ID)
    );

    /** Platform admins legitimately operate across tenants — exempt from this check. */
    private static final Set<String> EXEMPT_USER_TYPES = Set.of("SYSTEM");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper   objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            chain.doFilter(request, response);
            return;
        }

        String userType = TenantContext.getUserType();
        if (userType != null && EXEMPT_USER_TYPES.contains(userType)) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        for (OwnershipRule rule : RULES) {
            if (!pathMatcher.match(rule.pattern(), path)) {
                continue;
            }

            Map<String, String> vars = pathMatcher.extractUriTemplateVariables(rule.pattern(), path);
            String pathValue = vars.get(rule.variable());
            Integer claimValue = rule.claim() == Claim.USER_ID ? TenantContext.getUserId() : TenantContext.getTenantId();

            if (pathValue == null || claimValue == null || !pathValue.equals(String.valueOf(claimValue))) {
                log.error("RESOURCE OWNERSHIP VIOLATION — possible IDOR/tampering attempt "
                                + "[user={}] [userType={}] [claim={}={}] [pathVar={}={}] [path={}] [traceId={}]",
                        TenantContext.getUsername(), userType, rule.claim(), claimValue,
                        rule.variable(), pathValue, path, traceId(request));
                sendError(response, "SEC_RESOURCE_FORBIDDEN",
                        "You are not authorized to access this resource");
                return;
            }
            break; // matched and authorized — no need to evaluate further rules
        }

        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
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

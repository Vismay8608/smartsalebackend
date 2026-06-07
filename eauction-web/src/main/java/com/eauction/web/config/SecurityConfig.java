package com.eauction.web.config;

import com.eauction.admin.repository.UserRegisterRepository;
import com.eauction.web.filter.AdminApiProtectionFilter;
import com.eauction.web.filter.ConcurrentSessionFilter;
import com.eauction.web.filter.JwtAuthFilter;
import com.eauction.web.filter.PasswordExpiryFilter;
import com.eauction.web.filter.ResourceOwnershipFilter;
import com.eauction.web.repository.UserSessionRepository;
import com.eauction.web.service.SecurityConfigCacheService;
import com.eauction.web.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter           jwtAuthFilter;
    private final UserDetailsServiceImpl  userDetailsService;
    private final UserRegisterRepository  userRegisterRepository;
    private final UserSessionRepository   userSessionRepository;
    private final StringRedisTemplate     redisTemplate;
    private final ObjectMapper            objectMapper;
    private final SecurityConfigCacheService securityConfigCache;

    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/onboarding/sellers/individual",
            "/api/v1/onboarding/buyers/individual"
    };

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET,  SWAGGER_PATHS).permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            // ── Security filter chain order (post-JWT) ──────────────────────
            .addFilterBefore(jwtAuthFilter,              UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(passwordExpiryFilter(),      JwtAuthFilter.class)
            .addFilterAfter(concurrentSessionFilter(),   PasswordExpiryFilter.class)
            .addFilterAfter(adminApiProtectionFilter(),  ConcurrentSessionFilter.class)
            .addFilterAfter(resourceOwnershipFilter(),   AdminApiProtectionFilter.class)
            // ────────────────────────────────────────────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(
                        Map.of("success", false, "code", "AUTH005", "message", "Authentication required")));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(
                        Map.of("success", false, "code", "AUTHZ001", "message", "Access denied")));
                })
            );

        return http.build();
    }

    // ── Chain-only filters (no @Component — not auto-registered as servlet filters) ──

    @Bean
    public PasswordExpiryFilter passwordExpiryFilter() {
        return new PasswordExpiryFilter(userRegisterRepository, redisTemplate, objectMapper);
    }

    @Bean
    public ConcurrentSessionFilter concurrentSessionFilter() {
        return new ConcurrentSessionFilter(userSessionRepository, redisTemplate, objectMapper, securityConfigCache);
    }

    @Bean
    public AdminApiProtectionFilter adminApiProtectionFilter() {
        return new AdminApiProtectionFilter(redisTemplate, objectMapper);
    }

    @Bean
    public ResourceOwnershipFilter resourceOwnershipFilter() {
        return new ResourceOwnershipFilter(objectMapper);
    }

    // ── Standard Spring Security beans ────────────────────────────────────────

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "Authorization",
                "X-Trace-Id",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                "Retry-After",
                "Idempotency-Key",
                "X-Idempotency-Replayed",
                "X-Api-Key-Id",
                "X-Request-Timestamp",
                "X-Request-Nonce",
                "X-Api-Signature"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

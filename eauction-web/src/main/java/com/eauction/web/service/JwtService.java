package com.eauction.web.service;

import com.eauction.web.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateAccessToken(Integer userId, String username, Integer clientId,
                                       String userType, Set<String> permissions, UUID jti) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpirySeconds());

        return Jwts.builder()
                .id(jti.toString())
                .subject(username)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("userId",      userId)
                .claim("clientId",    clientId)
                .claim("userType",    userType)
                .claim("permissions", permissions)
                .signWith(signingKey())
                .compact();
    }

    public String generateRefreshToken() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes());
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    // ── Claim helpers ─────────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return parseAndValidate(token).getSubject();
    }

    public UUID extractJti(String token) {
        return UUID.fromString(parseAndValidate(token).getId());
    }

    public Integer extractUserId(String token) {
        return parseAndValidate(token).get("userId", Integer.class);
    }

    public Integer extractClientId(String token) {
        return parseAndValidate(token).get("clientId", Integer.class);
    }

    public String extractUserType(String token) {
        return parseAndValidate(token).get("userType", String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        List<String> perms = parseAndValidate(token).get("permissions", List.class);
        return perms == null ? Set.of() : new HashSet<>(perms);
    }

    public Date extractExpiry(String token) {
        return parseAndValidate(token).getExpiration();
    }

    public long getAccessTokenExpirySeconds() {
        return jwtProperties.getAccessTokenExpirySeconds();
    }

    public long getRefreshTokenExpirySeconds() {
        return jwtProperties.getRefreshTokenExpirySeconds();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

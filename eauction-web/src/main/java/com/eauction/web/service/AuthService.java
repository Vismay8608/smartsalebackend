package com.eauction.web.service;

import com.eauction.admin.entity.UserRegister;
import com.eauction.admin.repository.UserRegisterRepository;
import com.eauction.admin.repository.UserRoleRepository;
import com.eauction.admin.repository.RolePermissionRepository;
import com.eauction.common.enums.AccountStatus;
import com.eauction.common.exception.AppException;
import com.eauction.common.response.ResponseCode;
import com.eauction.common.util.HashUtil;
import com.eauction.web.entity.UserSession;
import com.eauction.web.filter.PasswordExpiryFilter;
import com.eauction.web.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRegisterRepository       userRegisterRepository;
    private final UserRoleRepository           userRoleRepository;
    private final RolePermissionRepository     rolePermissionRepository;
    private final UserSessionRepository        sessionRepository;
    private final JwtService                   jwtService;
    private final TokenBlacklistService        blacklistService;
    private final LoginAttemptService          loginAttemptService;
    private final SecurityConfigCacheService   securityConfigCache;
    private final DeviceService                deviceService;
    private final PasswordEncoder              passwordEncoder;
    private final StringRedisTemplate          redisTemplate;

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> login(String identifier, String rawPassword,
                                      String ipAddress, String platform, String userAgent, String deviceIdHeader) {

        // 1. Resolve user
        UserRegister user = userRegisterRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailure(null, identifier, null, ipAddress, platform, "USER_NOT_FOUND");
                    return AppException.unauthorized(ResponseCode.AUTH_INVALID_CREDENTIALS);
                });

        // 2. Lockout check (DB / in-memory before hitting DB main table)
        if (loginAttemptService.isLockedOut(user.getUserId())) {
            throw AppException.unauthorized(ResponseCode.AUTH_ACCOUNT_LOCKED);
        }

        // 3. Account state checks
        if (user.isDeleted()) {
            throw AppException.unauthorized(ResponseCode.AUTH_INVALID_CREDENTIALS);
        }
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            throw AppException.unauthorized(ResponseCode.AUTH_ACCOUNT_LOCKED);
        }
        if (user.getAccountStatus() == AccountStatus.INACTIVE || !Boolean.TRUE.equals(user.getIsActive())) {
            throw AppException.unauthorized(ResponseCode.AUTH_ACCOUNT_INACTIVE);
        }

        // 4. Password verification (BCrypt with pepper)
        String storedSalt = user.getPasswordSalt();
        if (!passwordEncoder.matches(rawPassword + storedSalt, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getUserId(), user.getUsername(),
                    user.getClient().getClientId(), ipAddress, platform, "BAD_CREDENTIALS");
            throw AppException.unauthorized(ResponseCode.AUTH_INVALID_CREDENTIALS);
        }

        // 4b. Device-limit enforcement — runs only after credentials are verified, so a
        // failed-login flood can't be used to exhaust a victim's device quota. Rejects
        // brand-new devices once the tenant's max_devices_allowed cap is reached; known
        // devices just get their last-seen info refreshed.
        String deviceId = (deviceIdHeader != null && !deviceIdHeader.isBlank())
                ? deviceIdHeader
                : HashUtil.sha512_256Hex(userAgent + "|" + platform);
        DeviceService.Registration deviceReg = deviceService.registerOrTouch(user.getUserId(), user.getClient().getClientId(),
                deviceId, platform, ipAddress,
                securityConfigCache.get(user.getClient().getClientId()).maxDevicesAllowed());
        Integer deviceRowId = deviceReg.deviceRowId();

        // 5. Load permissions
        List<Integer> roleIds = userRoleRepository.findActiveRoleIdsByUserId(user.getUserId());
        Set<String> permissions = roleIds.isEmpty()
                ? Set.of()
                : rolePermissionRepository.findPermissionCodesByRoleIds(roleIds);

        // 6. Issue tokens
        UUID jti       = UUID.randomUUID();
        String token   = jwtService.generateAccessToken(
                user.getUserId(), user.getUsername(),
                user.getClient().getClientId(),
                user.getUserType().name(), permissions, jti);
        String refresh = jwtService.generateRefreshToken();

        OffsetDateTime now           = OffsetDateTime.now();
        OffsetDateTime tokenExpiry   = now.plusSeconds(jwtService.getAccessTokenExpirySeconds());
        OffsetDateTime refreshExpiry = now.plusSeconds(jwtService.getRefreshTokenExpirySeconds());

        // 7. Persist session
        String sessionId = HashUtil.generateSecureToken();
        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .userId(user.getUserId())
                .clientId(user.getClient().getClientId())
                .deviceId(deviceRowId)
                .jti(jti)
                .token(token)
                .refreshToken(refresh)
                .refreshTokenExpiresAt(refreshExpiry)
                .platform(platform)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .issuedAt(now)
                .expiresAt(tokenExpiry)
                .lastActivityAt(now)
                .isActive(true)
                .isRevoked(false)
                .build();
        sessionRepository.save(session);

        // 8. Audit
        userRegisterRepository.updateLoginTimestamp(user.getUserId(), now);
        loginAttemptService.recordSuccess(user.getUserId(), user.getUsername(),
                user.getClient().getClientId(), ipAddress, platform, sessionId);

        // Warm the Redis password-policy cache right at login, so PasswordExpiryFilter
        // never needs to hit Postgres for this user — it reads straight from Redis on
        // every subsequent authenticated request (see PasswordExpiryFilter javadoc).
        PasswordExpiryFilter.writeCache(redisTemplate, user.getUserId(),
                Boolean.TRUE.equals(user.getForcePasswordChange()), user.getPasswordExpiryDate());

        log.info("Login successful [userId={}, username={}, platform={}]",
                user.getUserId(), user.getUsername(), platform);

        // 9. Build response
        return buildLoginResponse(user, token, refresh,
                jwtService.getAccessTokenExpirySeconds(), sessionId, permissions,
                deviceId, deviceReg);
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> refreshToken(String refreshToken, String ipAddress) {
        UserSession session = sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> AppException.unauthorized(ResponseCode.AUTH_REFRESH_TOKEN_INVALID));

        if (Boolean.TRUE.equals(session.getIsRevoked())) {
            throw AppException.unauthorized(ResponseCode.AUTH_TOKEN_REVOKED);
        }
        if (session.getRefreshTokenExpiresAt().isBefore(OffsetDateTime.now())) {
            throw AppException.unauthorized(ResponseCode.AUTH_REFRESH_TOKEN_EXPIRED);
        }

        UserRegister user = userRegisterRepository.findById(session.getUserId())
                .orElseThrow(() -> AppException.notFound(ResponseCode.USER_NOT_FOUND));

        if (!user.isEnabled()) {
            throw AppException.unauthorized(ResponseCode.AUTH_ACCOUNT_INACTIVE);
        }

        // Blacklist old JTI
        if (session.getJti() != null) {
            blacklistService.blacklist(session.getJti(), user.getUserId(),
                    session.getSessionId(), "TOKEN_ROTATION",
                    jwtService.extractExpiry(session.getToken()), null);
        }

        // Issue new access token
        List<Integer> roleIds = userRoleRepository.findActiveRoleIdsByUserId(user.getUserId());
        Set<String> permissions = roleIds.isEmpty()
                ? Set.of()
                : rolePermissionRepository.findPermissionCodesByRoleIds(roleIds);

        UUID newJti     = UUID.randomUUID();
        String newToken = jwtService.generateAccessToken(
                user.getUserId(), user.getUsername(),
                user.getClient().getClientId(),
                user.getUserType().name(), permissions, newJti);

        OffsetDateTime now       = OffsetDateTime.now();
        OffsetDateTime newExpiry = now.plusSeconds(jwtService.getAccessTokenExpirySeconds());

        session.setJti(newJti);
        session.setToken(newToken);
        session.setExpiresAt(newExpiry);
        session.setLastRefreshAt(now);
        session.setRefreshCount((short)(session.getRefreshCount() + 1));
        session.setLastActivityAt(now);
        sessionRepository.save(session);

        log.info("Token refreshed [userId={}, sessionId={}]", user.getUserId(), session.getSessionId());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("accessToken",  newToken);
        resp.put("tokenType",    "Bearer");
        resp.put("expiresIn",    jwtService.getAccessTokenExpirySeconds());
        resp.put("sessionId",    session.getSessionId());
        return resp;
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String token, String sessionId, Integer userId) {
        try {
            UUID jti = jwtService.extractJti(token);
            blacklistService.blacklist(jti, userId, sessionId, "LOGOUT",
                    jwtService.extractExpiry(token), userId);
        } catch (Exception ex) {
            log.warn("Could not extract JTI during logout: {}", ex.getMessage());
        }

        sessionRepository.findBySessionId(sessionId).ifPresent(s -> {
            s.setIsActive(false);
            s.setIsRevoked(true);
            s.setRevokedAt(OffsetDateTime.now());
            s.setRevokeReason("LOGOUT");
            s.setLogoutType("MANUAL");
            sessionRepository.save(s);
        });
        log.info("Logout successful [userId={}, sessionId={}]", userId, sessionId);
    }

    @Transactional
    public void logoutAll(Integer userId, String currentToken) {
        try {
            UUID jti = jwtService.extractJti(currentToken);
            blacklistService.blacklist(jti, userId, null, "LOGOUT",
                    jwtService.extractExpiry(currentToken), userId);
        } catch (Exception ignored) {}

        sessionRepository.revokeAllForUser(userId, OffsetDateTime.now(), "LOGOUT_ALL");
        log.info("All sessions revoked [userId={}]", userId);
    }

    // ── Change password ───────────────────────────────────────────────────────

    @Transactional
    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        UserRegister user = userRegisterRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound(ResponseCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword + user.getPasswordSalt(), user.getPasswordHash())) {
            throw AppException.badRequest(ResponseCode.AUTH_CURRENT_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(newPassword + user.getPasswordSalt(), user.getPasswordHash())) {
            throw AppException.badRequest(ResponseCode.AUTH_PASSWORD_REUSE_NOT_ALLOWED);
        }

        int expiryDays = securityConfigCache.get(user.getClient().getClientId()).passwordExpiryDays();

        String newSalt = HashUtil.generateSalt();
        OffsetDateTime now       = OffsetDateTime.now();
        OffsetDateTime newExpiry = now.plusDays(expiryDays);

        user.setPasswordHash(passwordEncoder.encode(newPassword + newSalt));
        user.setPasswordSalt(newSalt);
        user.setForcePasswordChange(false);
        user.setPasswordChangedAt(now);
        user.setPasswordExpiryDate(newExpiry);
        userRegisterRepository.save(user);

        // Refresh the cache immediately — otherwise PasswordExpiryFilter would keep
        // enforcing the old (forceChange=true / expired) snapshot until CACHE_TTL lapses,
        // locking the user out right after they did exactly what was asked of them.
        PasswordExpiryFilter.writeCache(redisTemplate, userId, false, newExpiry);

        log.info("Password changed successfully [userId={}]", userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildLoginResponse(UserRegister user, String accessToken,
                                                    String refreshToken, long expiresIn,
                                                    String sessionId, Set<String> permissions,
                                                    String deviceId, DeviceService.Registration deviceReg) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("accessToken",  accessToken);
        resp.put("refreshToken", refreshToken);
        resp.put("tokenType",    "Bearer");
        resp.put("expiresIn",    expiresIn);
        resp.put("sessionId",    sessionId);
        resp.put("user", Map.of(
                "userId",              user.getUserId(),
                "username",            user.getUsername(),
                "email",               user.getEmail(),
                "userType",            user.getUserType(),
                "clientId",            user.getClient().getClientId(),
                "forcePasswordChange", user.getForcePasswordChange()
        ));
        resp.put("permissions", permissions);
        resp.put("device", Map.of(
                "deviceId",     deviceId,
                "deviceRowId",  deviceReg.deviceRowId(),
                "isNewDevice",  deviceReg.isNewDevice()
        ));
        return resp;
    }

    public String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}

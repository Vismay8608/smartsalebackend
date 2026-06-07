package com.eauction.web.service;

import com.eauction.admin.repository.UserRegisterRepository;
import com.eauction.common.enums.AccountStatus;
import com.eauction.web.entity.UserFailedLogin;
import com.eauction.web.entity.UserLoginAttempt;
import com.eauction.web.repository.UserFailedLoginRepository;
import com.eauction.web.repository.UserLoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserFailedLoginRepository  failedLoginRepository;
    private final UserLoginAttemptRepository loginAttemptRepository;
    private final UserRegisterRepository     userRegisterRepository;
    private final SecurityConfigCacheService securityConfigCache;

    @Transactional
    public void recordSuccess(Integer userId, String username, Integer clientId,
                               String ipAddress, String platform, String sessionId) {
        failedLoginRepository.resetFailedAttempts(userId);
        loginAttemptRepository.save(UserLoginAttempt.builder()
                .userId(userId).username(username).clientId(clientId)
                .attemptType("SUCCESS").ipAddress(ipAddress)
                .platform(platform).sessionId(sessionId)
                .timestamp(OffsetDateTime.now()).build());
    }

    @Transactional
    public void recordFailure(Integer userId, String username, Integer clientId,
                               String ipAddress, String platform, String reason) {
        loginAttemptRepository.save(UserLoginAttempt.builder()
                .userId(userId).username(username).clientId(clientId)
                .attemptType("FAILED").ipAddress(ipAddress)
                .platform(platform).failureReason(reason)
                .timestamp(OffsetDateTime.now()).build());

        if (userId == null) return;

        SecurityConfigCacheService.ConfigSnapshot config = securityConfigCache.get(clientId);
        int  maxAttempts    = config.maxFailedLoginAttempts();
        long lockoutMinutes = config.accountLockoutDurationMinutes();

        UserFailedLogin record = failedLoginRepository.findByUserId(userId)
                .orElseGet(() -> UserFailedLogin.builder().userId(userId).failedAttemptCount(0).build());

        record.setFailedAttemptCount(record.getFailedAttemptCount() + 1);
        record.setLastFailedAttempt(OffsetDateTime.now());
        record.setLastAttemptIp(ipAddress);
        record.setLastAttemptPlatform(platform);

        if (record.getFailedAttemptCount() >= maxAttempts) {
            record.setAccountLocked(true);
            record.setLockUntil(OffsetDateTime.now().plusMinutes(lockoutMinutes));
            record.setLockReason("Too many failed login attempts");
            lockUserAccount(userId);
            log.warn("Account locked after {} failed attempts [userId={}]", maxAttempts, userId);
        }
        failedLoginRepository.save(record);
    }

    public boolean isLockedOut(Integer userId) {
        return failedLoginRepository.findByUserId(userId)
                .map(UserFailedLogin::isCurrentlyLocked)
                .orElse(false);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void lockUserAccount(Integer userId) {
        userRegisterRepository.findById(userId).ifPresent(u -> {
            u.setAccountStatus(AccountStatus.LOCKED);
            userRegisterRepository.save(u);
        });
    }
}

package com.eauction.web.service;

import com.eauction.common.exception.AppException;
import com.eauction.common.response.ResponseCode;
import com.eauction.web.entity.UserDevice;
import com.eauction.web.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Tracks devices a user has logged in from and enforces the per-tenant
 * {@code max_devices_allowed} policy from {@code user_security_configs} at login time.
 *
 * A "device" is identified by the client-supplied {@code X-Device-Id} header
 * (falls back to a hash of User-Agent+platform for clients that don't send one).
 * Known devices simply get their last-seen info refreshed; a brand-new device
 * is only registered if the user hasn't already reached their device cap —
 * otherwise the login is rejected before any session/token is created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository deviceRepository;

    /** Outcome of a registration check — lets callers tell new vs. recognized devices apart (e.g. for login-response UX). */
    public record Registration(Integer deviceRowId, boolean isNewDevice) {}

    @Transactional
    public Registration registerOrTouch(Integer userId, Integer clientId, String deviceId,
                                         String platform, String ipAddress, int maxDevicesAllowed) {

        OffsetDateTime now = OffsetDateTime.now();

        Optional<UserDevice> existing = deviceRepository.findByUserIdAndDeviceIdAndIsRevokedFalse(userId, deviceId);
        if (existing.isPresent()) {
            UserDevice device = existing.get();
            device.setLastUsedAt(now);
            device.setIpAddress(ipAddress);
            return new Registration(deviceRepository.save(device).getId(), false);
        }

        long activeCount = deviceRepository.countByUserIdAndIsRevokedFalse(userId);
        if (activeCount >= maxDevicesAllowed) {
            log.warn("Device limit exceeded [userId={}] [active={}] [max={}]", userId, activeCount, maxDevicesAllowed);
            throw AppException.forbidden(ResponseCode.AUTH_DEVICE_LIMIT_EXCEEDED);
        }

        UserDevice device = UserDevice.builder()
                .userId(userId)
                .clientId(clientId)
                .deviceId(deviceId)
                .deviceType(deviceTypeFor(platform))
                .ipAddress(ipAddress)
                .isTrusted(false)
                .isRevoked(false)
                .registeredAt(now)
                .lastUsedAt(now)
                .build();

        Integer id = deviceRepository.save(device).getId();
        log.info("New device registered [userId={}] [deviceId={}] [type={}]", userId, deviceId, device.getDeviceType());
        return new Registration(id, true);
    }

    private static String deviceTypeFor(String platform) {
        if (platform == null) return "OTHER";
        return switch (platform) {
            case "MOBILE_ANDROID", "MOBILE_IOS" -> "MOBILE";
            case "WEB", "DESKTOP" -> "DESKTOP";
            default -> "OTHER";
        };
    }
}

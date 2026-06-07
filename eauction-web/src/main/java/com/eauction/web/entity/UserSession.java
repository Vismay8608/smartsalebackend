package com.eauction.web.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "session_id", nullable = false, unique = true, length = 500)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "client_id")
    private Integer clientId;

    @Column(name = "device_id")
    private Integer deviceId;

    @Column(name = "jti")
    private UUID jti;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @Column(name = "token_type", length = 50)
    @Builder.Default
    private String tokenType = "JWT";

    @Column(name = "refresh_token_expires_at")
    private OffsetDateTime refreshTokenExpiresAt;

    @Column(name = "refresh_count")
    @Builder.Default
    private Short refreshCount = 0;

    @Column(name = "last_refresh_at")
    private OffsetDateTime lastRefreshAt;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "app_state", length = 20)
    @Builder.Default
    private String appState = "ACTIVE";

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "location_city", length = 100)
    private String locationCity;

    @Column(name = "location_country", length = 100)
    private String locationCountry;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_revoked")
    @Builder.Default
    private Boolean isRevoked = false;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;

    @Column(name = "logout_type", length = 20)
    private String logoutType;
}

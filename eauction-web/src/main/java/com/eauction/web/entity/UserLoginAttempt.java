package com.eauction.web.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_login_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "userid")
    private Integer userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "client_id")
    private Integer clientId;

    @Column(name = "attempt_type", length = 15)
    private String attemptType;   // SUCCESS | FAILED | LOCKED

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "session_id", length = 500)
    private String sessionId;

    @Column(name = "jti")
    private UUID jti;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "is_suspicious")
    @Builder.Default
    private Boolean isSuspicious = false;

    @Column(name = "risk_score")
    @Builder.Default
    private Short riskScore = 0;

    @Column(name = "timestamp")
    private OffsetDateTime timestamp;
}

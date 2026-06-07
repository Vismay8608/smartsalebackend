package com.eauction.web.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_failed_logins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFailedLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "userid", nullable = false, unique = true)
    private Integer userId;

    @Column(name = "failed_attempt_count")
    @Builder.Default
    private Integer failedAttemptCount = 0;

    @Column(name = "last_failed_attempt")
    private OffsetDateTime lastFailedAttempt;

    @Column(name = "last_attempt_ip", length = 45)
    private String lastAttemptIp;

    @Column(name = "last_attempt_platform", length = 20)
    private String lastAttemptPlatform;

    @Column(name = "account_locked")
    @Builder.Default
    private Boolean accountLocked = false;

    @Column(name = "lock_until")
    private OffsetDateTime lockUntil;

    @Column(name = "lock_reason", length = 255)
    private String lockReason;

    @Column(name = "unlock_token", length = 255)
    private String unlockToken;

    @Column(name = "auto_unlock_token_expires_at")
    private OffsetDateTime autoUnlockTokenExpiresAt;

    @Column(name = "notify_sent")
    @Builder.Default
    private Boolean notifySent = false;

    @Column(name = "notify_sent_at")
    private OffsetDateTime notifySentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public boolean isCurrentlyLocked() {
        return Boolean.TRUE.equals(accountLocked)
                && (lockUntil == null || lockUntil.isAfter(OffsetDateTime.now()));
    }
}

package com.eauction.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_security_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSecurityConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private ClientRegister client;

    @Column(name = "password_min_length") @Builder.Default private Integer passwordMinLength = 8;
    @Column(name = "password_max_length") @Builder.Default private Integer passwordMaxLength = 128;
    @Column(name = "password_require_uppercase") @Builder.Default private Boolean passwordRequireUppercase = true;
    @Column(name = "password_require_lowercase") @Builder.Default private Boolean passwordRequireLowercase = true;
    @Column(name = "password_require_numbers") @Builder.Default private Boolean passwordRequireNumbers = true;
    @Column(name = "password_require_special_chars") @Builder.Default private Boolean passwordRequireSpecialChars = true;
    @Column(name = "password_special_chars_regex", length = 255) @Builder.Default private String passwordSpecialCharsRegex = "!@#$%^&*()";
    @Column(name = "password_expiry_days") @Builder.Default private Integer passwordExpiryDays = 90;
    @Column(name = "password_history_count") @Builder.Default private Integer passwordHistoryCount = 5;
    @Column(name = "password_policy_regex") private String passwordPolicyRegex;
    @Column(name = "mfa_required") @Builder.Default private Boolean mfaRequired = false;
    @Column(name = "mfa_type", length = 25) private String mfaType;
    @Column(name = "session_timeout_minutes") @Builder.Default private Integer sessionTimeoutMinutes = 30;
    @Column(name = "session_absolute_timeout_hours") @Builder.Default private Integer sessionAbsoluteTimeoutHours = 8;
    @Column(name = "max_concurrent_sessions") @Builder.Default private Integer maxConcurrentSessions = 5;
    @Column(name = "max_devices_allowed") @Builder.Default private Integer maxDevicesAllowed = 3;
    @Column(name = "max_failed_user_login_attempts") @Builder.Default private Integer maxFailedUserLoginAttempts = 5;
    @Column(name = "account_lockout_duration_minutes") @Builder.Default private Integer accountLockoutDurationMinutes = 30;
    @Column(name = "ip_whitelist_enabled") @Builder.Default private Boolean ipWhitelistEnabled = false;
    @Column(name = "ip_blacklist_enabled") @Builder.Default private Boolean ipBlacklistEnabled = false;
    @Column(name = "device_verification_required") @Builder.Default private Boolean deviceVerificationRequired = false;
    @Column(name = "force_password_change_on_first_login") @Builder.Default private Boolean forcePasswordChangeOnFirstLogin = true;
    @Column(name = "force_password_change_on_admin_reset") @Builder.Default private Boolean forcePasswordChangeOnAdminReset = true;
    @Column(name = "login_notification") @Builder.Default private Boolean loginNotification = true;
    @Column(name = "suspicious_activity_notification") @Builder.Default private Boolean suspiciousActivityNotification = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

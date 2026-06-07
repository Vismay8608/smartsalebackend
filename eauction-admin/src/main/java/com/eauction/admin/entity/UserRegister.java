package com.eauction.admin.entity;

import com.eauction.common.enums.AccountStatus;
import com.eauction.common.enums.UserType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_register")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userid")
    private Integer userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clientid", nullable = false)
    private ClientRegister client;

    // branchid stored as plain FK to avoid circular entity init issues
    @Column(name = "branchid")
    private Integer branchId;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "phone_verified")
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "phone_verified_at")
    private OffsetDateTime phoneVerifiedAt;

    @Column(name = "password_hash", nullable = false, length = 500)
    private String passwordHash;

    @Column(name = "password_salt", nullable = false, length = 100)
    private String passwordSalt;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 25)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", length = 30)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "mfa_enabled")
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_type", length = 10)
    private String mfaType;

    @Column(name = "mfa_secret", length = 255)
    private String mfaSecret;

    @Column(name = "force_password_change")
    @Builder.Default
    private Boolean forcePasswordChange = true;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "previous_login_at")
    private OffsetDateTime previousLoginAt;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Column(name = "password_expiry_date")
    private OffsetDateTime passwordExpiryDate;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_by")
    private Integer createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Integer updatedBy;

    public boolean isDeleted()  { return deletedAt != null; }
    public boolean isLocked()   { return AccountStatus.LOCKED == accountStatus; }
    public boolean isEnabled()  { return AccountStatus.ACTIVE == accountStatus && Boolean.TRUE.equals(isActive) && !isDeleted(); }
}

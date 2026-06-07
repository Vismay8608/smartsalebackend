package com.eauction.web.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_token_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTokenBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "blacklist_id")
    private Integer blacklistId;

    @Column(name = "jti", nullable = false, unique = true)
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "session_id", length = 500)
    private String sessionId;

    @CreationTimestamp
    @Column(name = "revoked_at", nullable = false, updatable = false)
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by")
    private Integer revokedBy;

    @Column(name = "revoke_reason", length = 30)
    private String revokeReason;

    @Column(name = "original_expires_at", nullable = false)
    private OffsetDateTime originalExpiresAt;

    @Column(name = "is_cleaned")
    @Builder.Default
    private Boolean isCleaned = false;
}

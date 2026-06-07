package com.eauction.admin.entity;

import com.eauction.common.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "client_user_kyc")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientUserKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kyc_id")
    private Integer kycId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clientid", nullable = false)
    private ClientRegister client;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "kyc_subject", nullable = false, length = 15)
    private String kycSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 10)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "gst_masked", length = 15)
    private String gstMasked;

    @Column(name = "gst_hash", length = 64)
    private String gstHash;

    @Column(name = "cin_masked", length = 21)
    private String cinMasked;

    @Column(name = "cin_hash", length = 64)
    private String cinHash;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "pan_masked", length = 10)
    private String panMasked;

    @Column(name = "pan_hash", length = 64)
    private String panHash;

    @Column(name = "aadhaar_masked", length = 12)
    private String aadhaarMasked;

    @Column(name = "aadhaar_hash", length = 64)
    private String aadhaarHash;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "remarks")
    private String remarks;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "created_by")
    private Integer createdBy;
}

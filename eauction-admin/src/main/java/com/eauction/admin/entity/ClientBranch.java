package com.eauction.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branchid")
    private Integer branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clientid", nullable = false)
    private ClientRegister client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", nullable = false)
    private ClientBranchLevel level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ClientBranch parent;

    @Column(name = "branch_code", nullable = false, unique = true, length = 50)
    private String branchCode;

    @Column(name = "branch_name", nullable = false, length = 255)
    private String branchName;

    @Column(name = "branch_country", length = 100)
    private String branchCountry;

    @Column(name = "branch_state", length = 100)
    private String branchState;

    @Column(name = "branch_district", length = 100)
    private String branchDistrict;

    @Column(name = "branch_city", length = 100)
    private String branchCity;

    @Column(name = "branch_address")
    private String branchAddress;

    @Column(name = "pin_code", length = 20)
    private String pinCode;

    @Column(name = "phone_primary", length = 20)
    private String phonePrimary;

    @Column(name = "phone_secondary", length = 20)
    private String phoneSecondary;

    @Column(name = "email_primary", length = 255)
    private String emailPrimary;

    @Column(name = "email_secondary", length = 255)
    private String emailSecondary;

    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "issystemgenerated", nullable = false)
    @Builder.Default
    private Boolean isSystemGenerated = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private Integer createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Integer updatedBy;
}

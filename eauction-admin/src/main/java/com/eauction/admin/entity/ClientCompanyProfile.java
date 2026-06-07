package com.eauction.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "client_company_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientCompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Integer profileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clientid", nullable = false, unique = true)
    private ClientRegister client;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "company_name_short", length = 50)
    private String companyNameShort;

    @Column(name = "company_legal_name", length = 255)
    private String companyLegalName;

    @Column(name = "company_type", length = 30)
    private String companyType;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "establishment_date")
    private LocalDate establishmentDate;

    @Column(name = "incorporation_date")
    private LocalDate incorporationDate;

    @Column(name = "financial_year_start")
    private Short financialYearStart;

    @Column(name = "financial_year_end")
    private Short financialYearEnd;

    @Column(name = "number_of_employees")
    private Integer numberOfEmployees;

    @Column(name = "annual_turnover", precision = 20, scale = 2)
    private BigDecimal annualTurnover;

    @Column(name = "business_category", length = 100)
    private String businessCategory;

    @Column(name = "industry_type", length = 100)
    private String industryType;

    @Column(name = "classification", length = 100)
    private String classification;

    @Column(name = "mobile_secondary", length = 20)
    private String mobileSecondary;

    @Column(name = "mobile_secondary_verified", nullable = false)
    @Builder.Default
    private Boolean mobileSecondaryVerified = false;

    @Column(name = "mobile_secondary_verified_at")
    private OffsetDateTime mobileSecondaryVerifiedAt;

    @Column(name = "fax_number", length = 20)
    private String faxNumber;

    @Column(name = "email_secondary", length = 255)
    private String emailSecondary;

    @Column(name = "email_secondary_verified", nullable = false)
    @Builder.Default
    private Boolean emailSecondaryVerified = false;

    @Column(name = "email_secondary_verified_at")
    private OffsetDateTime emailSecondaryVerifiedAt;

    @Column(name = "support_email", length = 255)
    private String supportEmail;

    @Column(name = "support_email_verified", nullable = false)
    @Builder.Default
    private Boolean supportEmailVerified = false;

    @Column(name = "support_email_verified_at")
    private OffsetDateTime supportEmailVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Integer updatedBy;
}

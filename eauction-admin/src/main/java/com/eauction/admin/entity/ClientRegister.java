package com.eauction.admin.entity;

import com.eauction.common.entity.BaseAuditEntity;
import com.eauction.common.enums.AccountStatus;
import com.eauction.common.enums.ClientCategory;
import com.eauction.common.enums.ClientType;
import com.eauction.common.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "client_register")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientRegister extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "clientid")
    private Integer clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_category", length = 10)
    private ClientCategory clientCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", length = 15)
    private ClientType clientType;

    @Column(name = "mobile_primary", length = 20)
    private String mobilePrimary;

    @Column(name = "mobile_primary_verified", nullable = false)
    @Builder.Default
    private Boolean mobilePrimaryVerified = false;

    @Column(name = "mobile_primary_verified_at")
    private OffsetDateTime mobilePrimaryVerifiedAt;

    @Column(name = "email_primary", length = 255)
    private String emailPrimary;

    @Column(name = "email_primary_otp", length = 10)
    private String emailPrimaryOtp;

    @Column(name = "email_primary_verified_at")
    private OffsetDateTime emailPrimaryVerifiedAt;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", length = 100)
    @Builder.Default
    private String country = "India";

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 40)
    @Builder.Default
    private RegistrationStatus registrationStatus = RegistrationStatus.REGISTRATION_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "accountstatus", nullable = false, length = 30)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "adminusername", length = 100)
    private String adminUsername;

    @Column(name = "adminuserid")
    private Integer adminUserId;

    public boolean isEmailVerified() {
        return emailPrimaryVerifiedAt != null;
    }
}

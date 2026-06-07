package com.eauction.admin.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record KycSubmitRequest(

        @NotBlank(message = "KYC subject is required")
        @Pattern(regexp = "SYSTEM_EMPLOYEE|SELLER_COMPANY|SELLER_EMPLOYEE|SELLER_INDIVIDUAL|BUYER_COMPANY|BUYER_INDIVIDUAL",
                 message = "Invalid KYC subject")
        String kycSubject,

        @Size(max = 200)
        String fullName,

        LocalDate dateOfBirth,

        @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "Invalid PAN format")
        String panNumber,

        @Pattern(regexp = "^[0-9]{12}$", message = "Invalid Aadhaar number")
        String aadhaarNumber,

        @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "Invalid GST number")
        String gstNumber,

        @Pattern(regexp = "^[A-Z][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$", message = "Invalid CIN number")
        String cinNumber,

        @Size(max = 1000)
        String remarks
) {}

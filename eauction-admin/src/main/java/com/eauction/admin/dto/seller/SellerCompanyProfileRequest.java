package com.eauction.admin.dto.seller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SellerCompanyProfileRequest(

        @NotBlank(message = "Company legal name is required")
        @Size(max = 255)
        String companyLegalName,

        String websiteUrl,
        LocalDate establishmentDate,
        LocalDate incorporationDate,

        Integer numberOfEmployees,
        BigDecimal annualTurnover,
        String businessCategory,
        String industryType,
        String classification,

        String mobileSecondary,
        String emailSecondary,
        String supportEmail,
        String faxNumber
) {}

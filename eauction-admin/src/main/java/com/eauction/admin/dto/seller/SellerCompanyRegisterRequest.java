package com.eauction.admin.dto.seller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerCompanyRegisterRequest(

        @NotBlank(message = "Company name is required")
        @Size(max = 255)
        String companyName,

        @Size(max = 50)
        String companyNameShort,

        @NotBlank(message = "Primary email is required")
        @Email(message = "Invalid email address")
        String emailPrimary,

        @NotBlank(message = "Primary mobile is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid mobile number")
        String mobilePrimary,

        String addressLine1,
        String addressLine2,
        String city,
        String state,

        @Size(max = 10)
        String pincode,

        // Company type: PRIVATE_LIMITED, PUBLIC_LIMITED, etc.
        String companyType
) {}

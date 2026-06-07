package com.eauction.admin.dto.buyer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BuyerCompanyRegisterRequest(

        @NotBlank @Size(max = 255) String companyName,
        @Size(max = 50) String companyNameShort,

        @NotBlank @Email @Size(max = 255) String emailPrimary,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid mobile number")
        String mobilePrimary,

        String addressLine1,
        String addressLine2,
        String city,
        String state,
        @Size(max = 10) String pincode,

        String companyType
) {}

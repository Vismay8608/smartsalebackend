package com.eauction.admin.dto.buyer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BuyerIndividualRegisterRequest(

        @NotBlank @Size(max = 100) String firstName,
        String middleName,
        @NotBlank @Size(max = 100) String lastName,

        @NotBlank
        @Size(min = 4, max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, dots, hyphens, underscores")
        String username,

        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid mobile number")
        String mobilePrimary,

        @NotBlank @Size(min = 8, max = 64) String password,

        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode
) {}

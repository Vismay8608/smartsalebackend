package com.eauction.admin.dto.seller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SellerBranchRequest(

        @NotNull(message = "Level ID is required")
        Integer levelId,

        Integer parentBranchId,

        @NotBlank(message = "Branch code is required")
        @Size(max = 50)
        String branchCode,

        @NotBlank(message = "Branch name is required")
        @Size(max = 255)
        String branchName,

        String branchCity,
        String branchState,
        String branchDistrict,
        String branchCountry,
        String branchAddress,
        String pinCode,
        String phonePrimary,
        String emailPrimary
) {}

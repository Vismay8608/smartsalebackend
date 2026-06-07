package com.eauction.admin.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KycRejectRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(max = 1000)
        String reason
) {}

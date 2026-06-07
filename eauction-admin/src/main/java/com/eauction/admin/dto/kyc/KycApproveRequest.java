package com.eauction.admin.dto.kyc;

import jakarta.validation.constraints.Size;

public record KycApproveRequest(
        @Size(max = 1000)
        String remarks
) {}

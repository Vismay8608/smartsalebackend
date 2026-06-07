package com.eauction.admin.dto.seller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SellerBranchLevelRequest(

        @NotEmpty(message = "At least one branch level is required")
        @Valid
        List<BranchLevel> levels
) {
    public record BranchLevel(
            String levelCode,
            String label,
            short levelOrder
    ) {}
}

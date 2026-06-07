package com.eauction.admin.controller.buyer;

import com.eauction.admin.dto.buyer.*;
import com.eauction.admin.service.buyer.BuyerOnboardingService;
import com.eauction.common.context.TenantContext;
import com.eauction.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Buyer Onboarding", description = "Onboarding for Company and Individual buyers")
public class BuyerOnboardingController {

    private final BuyerOnboardingService buyerOnboardingService;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // COMPANY BUYER
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @PostMapping("/api/v1/onboarding/buyers/company")
    @Operation(summary = "Step 1 – Register company buyer (creates client + provisions roles + root branch)")
    @PreAuthorize("hasAuthority('SYS.CLIENT.APPROVE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerCompany(
            @Valid @RequestBody BuyerCompanyRegisterRequest req) {
        return ApiResponse.created("Company buyer registration initiated",
                buyerOnboardingService.registerCompanyBuyer(req));
    }

    @PostMapping("/api/v1/onboarding/buyers/company/{clientId}/users")
    @Operation(summary = "Step 2 – Create the single authorised buyer user for the company")
    @PreAuthorize("hasAuthority('BUYER.USER.CREATE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(
            @PathVariable Integer clientId,
            @Valid @RequestBody BuyerCompanyUserRequest req) {
        return ApiResponse.created("Buyer user created",
                buyerOnboardingService.createCompanyBuyerUser(clientId, req, TenantContext.getUserId()));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // INDIVIDUAL BUYER
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @PostMapping("/api/v1/onboarding/buyers/individual")
    @Operation(summary = "Register individual buyer (single step – creates client + user)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerIndividual(
            @Valid @RequestBody BuyerIndividualRegisterRequest req) {
        return ApiResponse.created("Individual buyer registered",
                buyerOnboardingService.registerIndividualBuyer(req));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // COMMON
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @GetMapping("/api/v1/onboarding/buyers/{clientId}/status")
    @Operation(summary = "Get current onboarding status for a buyer client")
    @PreAuthorize("hasAuthority('SYS.CLIENT.READ')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@PathVariable Integer clientId) {
        return ApiResponse.ok(buyerOnboardingService.getOnboardingStatus(clientId));
    }
}

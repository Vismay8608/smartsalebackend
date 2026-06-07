package com.eauction.admin.controller.seller;

import com.eauction.admin.dto.seller.*;
import com.eauction.admin.service.seller.SellerOnboardingService;
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
@Tag(name = "Seller Onboarding", description = "Multi-step onboarding for Company and Individual sellers")
public class SellerOnboardingController {

    private final SellerOnboardingService sellerOnboardingService;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // COMPANY SELLER
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @PostMapping("/api/v1/onboarding/sellers/company")
    @Operation(summary = "Step 1 – Register company seller (creates client + company profile shell)")
    @PreAuthorize("hasAuthority('SYS.CLIENT.APPROVE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerCompany(
            @Valid @RequestBody SellerCompanyRegisterRequest req) {
        return ApiResponse.created("Company seller registration initiated", sellerOnboardingService.registerCompanySeller(req));
    }

    @PutMapping("/api/v1/onboarding/sellers/company/{clientId}/profile")
    @Operation(summary = "Step 2 – Complete company profile")
    @PreAuthorize("hasAuthority('SYS.CLIENT.APPROVE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(
            @PathVariable Integer clientId,
            @Valid @RequestBody SellerCompanyProfileRequest req) {
        return ApiResponse.ok("Profile updated", sellerOnboardingService.updateCompanyProfile(clientId, req, TenantContext.getUserId()));
    }

    @PostMapping("/api/v1/onboarding/sellers/company/{clientId}/branches/levels")
    @Operation(summary = "Step 3 – Define branch hierarchy levels + auto-create root branch")
    @PreAuthorize("hasAuthority('SYS.CLIENT.APPROVE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createLevels(
            @PathVariable Integer clientId,
            @Valid @RequestBody SellerBranchLevelRequest req) {
        return ApiResponse.created("Branch levels created", sellerOnboardingService.createBranchLevels(clientId, req, TenantContext.getUserId()));
    }

    @PostMapping("/api/v1/onboarding/sellers/company/{clientId}/branches")
    @Operation(summary = "Create additional branch under an existing parent")
    @PreAuthorize("hasAuthority('SELLER.BRANCH.CREATE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBranch(
            @PathVariable Integer clientId,
            @Valid @RequestBody SellerBranchRequest req) {
        return ApiResponse.created("Branch created", sellerOnboardingService.createBranch(clientId, req, TenantContext.getUserId()));
    }

    @PostMapping("/api/v1/onboarding/sellers/company/{clientId}/roles")
    @Operation(summary = "Step 4 – Provision default roles from system templates")
    @PreAuthorize("hasAuthority('SYS.CLIENT.APPROVE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> provisionRoles(
            @PathVariable Integer clientId) {
        return ApiResponse.created("Default roles provisioned", sellerOnboardingService.provisionDefaultRoles(clientId, TenantContext.getUserId()));
    }

    @PostMapping("/api/v1/onboarding/sellers/company/{clientId}/users")
    @Operation(summary = "Step 5 – Create a seller user under the company")
    @PreAuthorize("hasAuthority('SELLER.USER.CREATE')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(
            @PathVariable Integer clientId,
            @Valid @RequestBody SellerUserCreateRequest req) {
        return ApiResponse.created("Seller user created", sellerOnboardingService.createSellerUser(clientId, req, TenantContext.getUserId()));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // INDIVIDUAL SELLER
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @PostMapping("/api/v1/onboarding/sellers/individual")
    @Operation(summary = "Register individual seller (single step – creates client + user)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerIndividual(
            @Valid @RequestBody SellerIndividualRegisterRequest req) {
        return ApiResponse.created("Individual seller registered", sellerOnboardingService.registerIndividualSeller(req));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // COMMON
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @GetMapping("/api/v1/onboarding/sellers/{clientId}/status")
    @Operation(summary = "Get current onboarding status for a seller client")
    @PreAuthorize("hasAuthority('SYS.CLIENT.READ')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@PathVariable Integer clientId) {
        return ApiResponse.ok(sellerOnboardingService.getOnboardingStatus(clientId));
    }
}

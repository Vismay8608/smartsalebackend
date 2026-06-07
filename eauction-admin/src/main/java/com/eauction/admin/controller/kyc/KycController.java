package com.eauction.admin.controller.kyc;

import com.eauction.admin.dto.kyc.KycApproveRequest;
import com.eauction.admin.dto.kyc.KycRejectRequest;
import com.eauction.admin.dto.kyc.KycSubmitRequest;
import com.eauction.admin.service.kyc.KycService;
import com.eauction.common.context.TenantContext;
import com.eauction.common.enums.KycStatus;
import com.eauction.common.response.ApiResponse;
import com.eauction.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC Review", description = "Submit and review client user KYC verifications")
@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService kycService;

    @PostMapping("/submit")
    @Operation(summary = "Submit a KYC record for the current tenant/user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@Valid @RequestBody KycSubmitRequest req) {
        Integer clientId = TenantContext.getTenantId();
        Integer userId = TenantContext.getUserId();
        Map<String, Object> result = kycService.submit(clientId, userId, req, userId);
        return ApiResponse.created("KYC submitted successfully", result);
    }

    @GetMapping
    @Operation(summary = "List KYC submissions for the current tenant")
    @PreAuthorize("hasAuthority('SYS.KYC.REVIEW')")
    public ResponseEntity<PageResponse<Map<String, Object>>> list(
            @RequestParam(required = false) KycStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("submittedAt").descending());
        Integer clientId = TenantContext.getTenantId();
        return PageResponse.of(kycService.list(clientId, status, pageable));
    }

    @GetMapping("/{kycId}")
    @Operation(summary = "Get a KYC submission by ID")
    @PreAuthorize("hasAuthority('SYS.KYC.REVIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable Integer kycId) {
        Integer clientId = TenantContext.getTenantId();
        return ApiResponse.ok(kycService.get(clientId, kycId));
    }

    @PostMapping("/{kycId}/approve")
    @Operation(summary = "Approve a pending KYC submission")
    @PreAuthorize("hasAuthority('SYS.KYC.APPROVE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
            @PathVariable Integer kycId,
            @Valid @RequestBody KycApproveRequest req) {
        Integer clientId = TenantContext.getTenantId();
        Integer reviewerId = TenantContext.getUserId();
        return ApiResponse.ok("KYC approved", kycService.approve(clientId, kycId, req, reviewerId));
    }

    @PostMapping("/{kycId}/reject")
    @Operation(summary = "Reject a pending KYC submission")
    @PreAuthorize("hasAuthority('SYS.KYC.REJECT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
            @PathVariable Integer kycId,
            @Valid @RequestBody KycRejectRequest req) {
        Integer clientId = TenantContext.getTenantId();
        Integer reviewerId = TenantContext.getUserId();
        return ApiResponse.ok("KYC rejected", kycService.reject(clientId, kycId, req, reviewerId));
    }
}

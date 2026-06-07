package com.eauction.admin.controller.system;

import com.eauction.admin.dto.system.CreateSystemUserRequest;
import com.eauction.admin.service.system.SystemUserService;
import com.eauction.common.context.TenantContext;
import com.eauction.common.enums.AccountStatus;
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
@RequestMapping("/api/v1/admin/system/users")
@RequiredArgsConstructor
@Tag(name = "System User Management", description = "Create and manage platform system users (SUPER_ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class SystemUserController {

    private final SystemUserService systemUserService;

    @PostMapping
    @Operation(summary = "Create a system user")
    @PreAuthorize("hasAuthority('SYS.USER.CREATE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(
            @Valid @RequestBody CreateSystemUserRequest req) {
        Integer actorId = TenantContext.getUserId();
        Map<String, Object> result = systemUserService.createSystemUser(req, actorId);
        return ApiResponse.created("System user created successfully", result);
    }

    @GetMapping
    @Operation(summary = "List all system users")
    @PreAuthorize("hasAuthority('SYS.USER.READ')")
    public ResponseEntity<PageResponse<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PageResponse.of(systemUserService.listSystemUsers(pageable));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a system user by ID")
    @PreAuthorize("hasAuthority('SYS.USER.READ')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUser(@PathVariable Integer userId) {
        return ApiResponse.ok(systemUserService.getSystemUser(userId));
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Update system user account status")
    @PreAuthorize("hasAuthority('SYS.USER.BLOCK')")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Integer userId,
            @RequestParam AccountStatus status) {
        systemUserService.updateStatus(userId, status, TenantContext.getUserId());
        return ApiResponse.noContent("User status updated to " + status);
    }
}

package com.eauction.web.controller;

import com.eauction.common.context.TenantContext;
import com.eauction.common.response.ApiResponse;
import com.eauction.web.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, logout, token refresh")
public class AuthController {

    private final AuthService authService;

    // ─── Login ────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain JWT tokens")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpRequest) {

        String ip       = authService.extractIp(httpRequest);
        String platform = httpRequest.getHeader("X-Platform") != null
                        ? httpRequest.getHeader("X-Platform") : "WEB";
        String ua        = httpRequest.getHeader("User-Agent");
        String deviceId  = httpRequest.getHeader("X-Device-Id");

        Map<String, Object> result = authService.login(req.identifier(), req.password(), ip, platform, ua, deviceId);
        return ApiResponse.ok("Login successful", result);
    }

    // ─── Refresh token ────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Exchange refresh token for a new access token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            @Valid @RequestBody RefreshTokenRequest req,
            HttpServletRequest httpRequest) {

        Map<String, Object> result = authService.refreshToken(req.refreshToken(), authService.extractIp(httpRequest));
        return ApiResponse.ok("Token refreshed", result);
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Revoke current session token")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        String token     = extractBearerToken(httpRequest);
        String sessionId = httpRequest.getHeader("X-Session-Id");
        authService.logout(token, sessionId, TenantContext.getUserId());
        return ApiResponse.noContent("Logged out successfully");
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke all active sessions for the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> logoutAll(HttpServletRequest httpRequest) {
        String token = extractBearerToken(httpRequest);
        authService.logoutAll(TenantContext.getUserId(), token);
        return ApiResponse.noContent("All sessions revoked");
    }

    // ─── Change password ──────────────────────────────────────────────────────

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's password")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req) {

        authService.changePassword(TenantContext.getUserId(), req.currentPassword(), req.newPassword());
        return ApiResponse.noContent("Password changed successfully");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : "";
    }

    // ─── Request records (no separate DTO files needed) ──────────────────────

    public record LoginRequest(
            @NotBlank(message = "Username or email is required") String identifier,
            @NotBlank(message = "Password is required")          String password
    ) {}

    public record RefreshTokenRequest(
            @NotBlank(message = "Refresh token is required") String refreshToken
    ) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters") String newPassword
    ) {}
}

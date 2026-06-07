package com.eauction.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResponseCode {

    // ── Success ──────────────────────────────────────────────────────────────
    SUCCESS("200", "Operation successful"),
    CREATED("201", "Resource created successfully"),
    ACCEPTED("202", "Request accepted for processing"),

    // ── Authentication ────────────────────────────────────────────────────────
    AUTH_INVALID_CREDENTIALS("AUTH001", "Invalid username or password"),
    AUTH_ACCOUNT_LOCKED("AUTH002", "Account is locked due to multiple failed attempts"),
    AUTH_ACCOUNT_INACTIVE("AUTH003", "Account is inactive"),
    AUTH_TOKEN_EXPIRED("AUTH004", "Access token has expired"),
    AUTH_TOKEN_INVALID("AUTH005", "Invalid or malformed token"),
    AUTH_TOKEN_REVOKED("AUTH006", "Token has been revoked"),
    AUTH_REFRESH_TOKEN_EXPIRED("AUTH007", "Refresh token has expired"),
    AUTH_REFRESH_TOKEN_INVALID("AUTH008", "Invalid refresh token"),
    AUTH_SESSION_EXPIRED("AUTH009", "Session has expired"),
    AUTH_MFA_REQUIRED("AUTH010", "Multi-factor authentication required"),
    AUTH_DEVICE_NOT_TRUSTED("AUTH011", "Device not recognized – verification required"),
    AUTH_MAX_SESSIONS_EXCEEDED("AUTH012", "Maximum concurrent sessions exceeded"),
    AUTH_IP_RESTRICTED("AUTH013", "Access from this IP address is restricted"),
    AUTH_PASSWORD_CHANGE_REQUIRED("AUTH014", "Password change is required"),
    AUTH_EMAIL_NOT_VERIFIED("AUTH015", "Email address is not verified"),
    AUTH_CURRENT_PASSWORD_INCORRECT("AUTH016", "Current password is incorrect"),
    AUTH_PASSWORD_REUSE_NOT_ALLOWED("AUTH017", "New password must be different from the current password"),
    AUTH_DEVICE_LIMIT_EXCEEDED("AUTH018", "Maximum number of registered devices reached for this account"),

    // ── Authorization ─────────────────────────────────────────────────────────
    ACCESS_DENIED("AUTHZ001", "You do not have permission to perform this action"),
    INSUFFICIENT_PERMISSIONS("AUTHZ002", "Insufficient permissions for this operation"),
    CROSS_TENANT_ACCESS("AUTHZ003", "Cross-tenant access is not permitted"),

    // ── Validation ────────────────────────────────────────────────────────────
    VALIDATION_ERROR("VAL001", "Request validation failed"),
    INVALID_INPUT("VAL002", "Invalid input provided"),
    DUPLICATE_ENTRY("VAL003", "Duplicate entry – resource already exists"),

    // ── Client / Tenant ───────────────────────────────────────────────────────
    CLIENT_NOT_FOUND("CLT001", "Client not found"),
    CLIENT_ALREADY_EXISTS("CLT002", "Client already registered"),
    CLIENT_INACTIVE("CLT003", "Client account is inactive"),
    CLIENT_KYC_PENDING("CLT004", "Client KYC verification is pending"),
    CLIENT_ONBOARDING_INCOMPLETE("CLT005", "Client onboarding is not complete"),
    TENANT_RESOLUTION_FAILED("CLT006", "Unable to resolve tenant context"),

    // ── User ──────────────────────────────────────────────────────────────────
    USER_NOT_FOUND("USR001", "User not found"),
    USER_ALREADY_EXISTS("USR002", "Username or email already registered"),
    USER_INACTIVE("USR003", "User account is inactive"),
    USER_LOCKED("USR004", "User account is locked"),
    USER_DELETED("USR005", "User account has been deleted"),

    // ── Branch ────────────────────────────────────────────────────────────────
    BRANCH_NOT_FOUND("BRN001", "Branch not found"),
    BRANCH_ALREADY_EXISTS("BRN002", "Branch code already exists"),

    // ── Role / Permission ─────────────────────────────────────────────────────
    ROLE_NOT_FOUND("ROLE001", "Role not found"),
    ROLE_ALREADY_EXISTS("ROLE002", "Role code already exists for this tenant"),
    PERMISSION_NOT_FOUND("PERM001", "Permission not found"),
    PRIVILEGE_ESCALATION("PERM002", "Cannot assign permissions beyond your own"),

    // ── KYC ───────────────────────────────────────────────────────────────────
    KYC_NOT_FOUND("KYC001", "KYC record not found"),
    KYC_ALREADY_SUBMITTED("KYC002", "KYC already submitted and under review"),
    KYC_ALREADY_REVIEWED("KYC003", "KYC has already been reviewed"),

    // ── System ────────────────────────────────────────────────────────────────
    INTERNAL_ERROR("SYS001", "An internal server error occurred"),
    SERVICE_UNAVAILABLE("SYS002", "Service is temporarily unavailable"),
    DATABASE_ERROR("SYS003", "Database operation failed"),
    CACHE_ERROR("SYS004", "Cache operation failed");

    private final String code;
    private final String message;
}

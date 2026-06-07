package com.eauction.admin.dto.system;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSystemUserRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        String middleName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Username is required")
        @Size(min = 4, max = 100, message = "Username must be 4–100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, dots, hyphens, underscores")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be 8–64 characters")
        String password,

        @NotBlank(message = "Role code is required")
        String roleCode,

        String designation,
        String employeeCode,

        // Optional — if null, assigned to the system root branch
        Integer branchId
) {}

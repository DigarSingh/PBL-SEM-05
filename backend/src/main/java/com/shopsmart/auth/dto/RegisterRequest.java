package com.shopsmart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String password,
        @Pattern(regexp = "^\\+?[0-9][0-9 -]{6,18}$", message = "must be a valid phone number") String phone) {
}

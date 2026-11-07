package com.shopsmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = "^\\+?[0-9][0-9 -]{6,18}$", message = "must be a valid phone number") String phone) {
}

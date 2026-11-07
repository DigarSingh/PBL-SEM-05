package com.shopsmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @Size(max = 30) String label,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9][0-9 -]{6,18}$", message = "must be a valid phone number") String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @Size(max = 120) String landmark,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a valid 6-digit PIN code") String postalCode,
        @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO country code") String country,
        boolean isDefault) {
}

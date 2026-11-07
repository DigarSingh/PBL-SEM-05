package com.shopsmart.user.dto;

public record AddressResponse(
        Long id,
        String label,
        String fullName,
        String phone,
        String line1,
        String line2,
        String landmark,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault) {
}

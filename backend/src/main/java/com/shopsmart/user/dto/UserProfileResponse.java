package com.shopsmart.user.dto;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String avatarUrl,
        List<String> roles,
        boolean emailVerified,
        Instant createdAt) {
}

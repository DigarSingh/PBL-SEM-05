package com.shopsmart.auth.dto;

import com.shopsmart.user.dto.UserProfileResponse;
import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserProfileResponse user) {

    public static AuthResponse bearer(String accessToken, Instant expiresAt, UserProfileResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresAt, user);
    }
}

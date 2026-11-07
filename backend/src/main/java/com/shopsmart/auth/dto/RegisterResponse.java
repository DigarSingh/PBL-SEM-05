package com.shopsmart.auth.dto;

public record RegisterResponse(Long userId, String email, boolean emailVerificationRequired) {
}

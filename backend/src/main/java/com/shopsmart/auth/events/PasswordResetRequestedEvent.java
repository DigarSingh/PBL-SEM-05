package com.shopsmart.auth.events;

public record PasswordResetRequestedEvent(Long userId, String email, String fullName, String resetToken) {
}

package com.shopsmart.auth.events;

public record EmailVerifiedEvent(Long userId, String email, String fullName) {
}

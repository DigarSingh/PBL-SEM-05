package com.shopsmart.auth.events;

/** Published after registration; carries the raw verification token for the e-mail link only. */
public record UserRegisteredEvent(Long userId, String email, String fullName, String verificationToken) {
}

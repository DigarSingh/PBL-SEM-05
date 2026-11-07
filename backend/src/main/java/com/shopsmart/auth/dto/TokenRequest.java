package com.shopsmart.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Carries a single-use token from an e-mail link (verification). */
public record TokenRequest(@NotBlank @Size(max = 128) String token) {
}

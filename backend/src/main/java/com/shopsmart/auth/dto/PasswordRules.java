package com.shopsmart.auth.dto;

/** Shared password policy: at least one letter and one digit (length is validated separately). */
public final class PasswordRules {

    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String MESSAGE = "must contain at least one letter and one number";

    private PasswordRules() {
    }
}

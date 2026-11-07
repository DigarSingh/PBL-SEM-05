package com.shopsmart.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application configuration bound from the {@code app.*} namespace.
 * Validated at startup so a misconfigured deployment fails fast.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendUrl,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Security security,
        @Valid @NotNull Razorpay razorpay,
        @Valid @NotNull Ai ai,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Storage storage,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Seed seed,
        @Valid @NotNull Jobs jobs) {

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    public record Jwt(
            @NotBlank(message = "JWT_SECRET must be set (at least 32 characters)") String secret,
            @NotNull Duration expiration,
            @NotNull Duration refreshExpiration,
            @NotBlank String issuer) {
    }

    public record Security(
            boolean cookieSecure,
            boolean requireEmailVerification,
            @Min(1) int maxFailedLogins,
            @NotNull Duration lockoutDuration,
            @NotNull Duration emailVerificationTtl,
            @NotNull Duration passwordResetTtl) {
    }

    public record Razorpay(
            String keyId,
            String keySecret,
            String webhookSecret,
            @NotBlank String baseUrl,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {

        /** True when API credentials are present, i.e. online payments can be offered. */
        public boolean configured() {
            return StringUtils.hasText(keyId) && StringUtils.hasText(keySecret);
        }

        public boolean webhookConfigured() {
            return StringUtils.hasText(webhookSecret);
        }
    }

    public record Ai(
            boolean enabled,
            @NotBlank String baseUrl,
            String apiKey,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {
    }

    public record Mail(@NotBlank String from, @NotBlank String fromName) {
    }

    public record Storage(@NotBlank String uploadDir, @NotBlank String publicUrlPrefix) {
    }

    public record RateLimit(boolean enabled, @Min(1) int authRequestsPerMinute, @Min(1) int apiRequestsPerMinute) {
    }

    public record Seed(boolean enabled, String adminEmail, String adminPassword, String customerPassword) {
    }

    public record Jobs(boolean enabled, @NotNull Duration pendingPaymentCheckInterval) {
    }
}

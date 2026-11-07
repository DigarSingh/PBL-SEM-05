package com.shopsmart.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StoreSettingsRequest(
        @NotBlank @Size(max = 80) String storeName,
        @NotBlank @Email @Size(max = 255) String supportEmail,
        @NotNull @DecimalMin("0.0") @DecimalMax("0.5") @Digits(integer = 1, fraction = 4) BigDecimal taxRate,
        boolean pricesIncludeTax,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal shippingFee,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal freeShippingThreshold,
        @Min(0) @Max(10_000) int defaultLowStockThreshold,
        boolean codEnabled,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal codMaxOrderAmount,
        boolean razorpayEnabled,
        @Min(5) @Max(1440) int pendingPaymentTimeoutMinutes) {
}

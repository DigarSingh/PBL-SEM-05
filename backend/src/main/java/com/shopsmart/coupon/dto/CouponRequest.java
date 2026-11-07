package com.shopsmart.coupon.dto;

import com.shopsmart.coupon.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record CouponRequest(
        @NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain letters, digits, '_' and '-'") String code,
        @Size(max = 255) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal discountValue,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minOrderAmount,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant expiresAt,
        @Min(1) Integer usageLimit,
        @Min(1) Integer perUserLimit,
        Boolean active) {
}

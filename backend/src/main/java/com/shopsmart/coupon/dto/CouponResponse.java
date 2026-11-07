package com.shopsmart.coupon.dto;

import com.shopsmart.coupon.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(
        Long id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Instant startsAt,
        Instant expiresAt,
        Integer usageLimit,
        Integer perUserLimit,
        int usedCount,
        boolean active,
        Instant createdAt) {
}

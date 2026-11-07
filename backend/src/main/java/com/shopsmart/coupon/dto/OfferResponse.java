package com.shopsmart.coupon.dto;

import com.shopsmart.coupon.DiscountType;
import java.math.BigDecimal;
import java.time.Instant;

/** A currently redeemable coupon as shown to shoppers. */
public record OfferResponse(String code, String description, DiscountType discountType, BigDecimal discountValue,
                            BigDecimal minOrderAmount, BigDecimal maxDiscountAmount, Instant expiresAt) {
}

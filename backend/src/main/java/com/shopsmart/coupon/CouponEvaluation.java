package com.shopsmart.coupon;

import java.math.BigDecimal;

/** Outcome of checking a coupon against a cart: either a discount or the reason it does not apply. */
public record CouponEvaluation(String code, boolean valid, BigDecimal discount, String message, Coupon coupon) {

    public static CouponEvaluation invalid(String code, String message) {
        return new CouponEvaluation(code, false, BigDecimal.ZERO, message, null);
    }
}

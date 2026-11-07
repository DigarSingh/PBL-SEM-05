package com.shopsmart.coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public final class CouponRules {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private CouponRules() {
    }

    public static String format(BigDecimal value) {
        return "₹" + money(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static CouponEvaluation evaluate(Coupon coupon, BigDecimal subtotal, long userRedemptions, Instant now) {
        String code = coupon.getCode();
        BigDecimal amount = money(subtotal);

        if (!coupon.isActive()) {
            return CouponEvaluation.invalid(code, "This coupon is inactive");
        }
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            return CouponEvaluation.invalid(code, "This coupon is not active yet");
        }
        if (coupon.getExpiresAt() != null && !now.isBefore(coupon.getExpiresAt())) {
            return CouponEvaluation.invalid(code, "This coupon has expired");
        }
        if (coupon.getMinOrderAmount() != null && amount.compareTo(coupon.getMinOrderAmount()) < 0) {
            BigDecimal remaining = money(coupon.getMinOrderAmount().subtract(amount));
            return CouponEvaluation.invalid(code, "Add ₹" + remaining.setScale(2, RoundingMode.HALF_UP)
                    + " more to use this coupon");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            return CouponEvaluation.invalid(code, "This coupon has been fully redeemed");
        }
        if (coupon.getPerUserLimit() != null && userRedemptions >= coupon.getPerUserLimit()) {
            return CouponEvaluation.invalid(code, "You have already used this coupon");
        }

        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = amount.multiply(coupon.getDiscountValue())
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getDiscountValue();
        }
        if (coupon.getMaxDiscountAmount() != null) {
            discount = discount.min(coupon.getMaxDiscountAmount());
        }
        discount = money(discount).min(amount).max(ZERO);
        return new CouponEvaluation(code, true, discount, null, coupon);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }
}

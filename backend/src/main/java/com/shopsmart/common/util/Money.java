package com.shopsmart.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monetary helpers. All amounts are INR with two decimals; rounding is HALF_UP. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(long rupees) {
        return BigDecimal.valueOf(rupees).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Selling price after a percentage discount. Discounted prices are rounded to whole rupees
     * (as Indian storefronts display them); an undiscounted price is returned unchanged.
     */
    public static BigDecimal applyDiscount(BigDecimal listPrice, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.signum() == 0) {
            return of(listPrice);
        }
        BigDecimal factor = HUNDRED.subtract(discountPercent).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        return listPrice.multiply(factor).setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return of(amount.multiply(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** Amount in the smallest currency unit (paise), as payment gateways expect. */
    public static long toMinorUnits(BigDecimal amount) {
        return of(amount).movePointRight(2).longValueExact();
    }

    public static BigDecimal fromMinorUnits(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}

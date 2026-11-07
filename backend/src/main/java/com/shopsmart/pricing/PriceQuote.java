package com.shopsmart.pricing;

import java.math.BigDecimal;

/**
 * Server-calculated totals. {@code subtotal} is after product discounts; {@code taxAmount} is
 * the GST contained in (or added to) the total depending on {@code taxInclusive}.
 */
public record PriceQuote(
        int itemCount,
        BigDecimal mrpTotal,
        BigDecimal subtotal,
        BigDecimal productSavings,
        BigDecimal couponDiscount,
        BigDecimal shippingFee,
        BigDecimal freeShippingThreshold,
        BigDecimal amountToFreeShipping,
        BigDecimal taxRate,
        boolean taxInclusive,
        BigDecimal taxAmount,
        BigDecimal total,
        String currency) {
}

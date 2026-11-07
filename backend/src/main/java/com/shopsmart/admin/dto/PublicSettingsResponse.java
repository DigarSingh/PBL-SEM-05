package com.shopsmart.admin.dto;

import java.math.BigDecimal;

/** Storefront-safe configuration: pricing rules, enabled payment methods and the public Razorpay key id. */
public record PublicSettingsResponse(
        String storeName,
        String supportEmail,
        String currency,
        BigDecimal taxRate,
        boolean pricesIncludeTax,
        BigDecimal shippingFee,
        BigDecimal freeShippingThreshold,
        boolean codEnabled,
        BigDecimal codMaxOrderAmount,
        boolean onlinePaymentsEnabled,
        String razorpayKeyId) {
}

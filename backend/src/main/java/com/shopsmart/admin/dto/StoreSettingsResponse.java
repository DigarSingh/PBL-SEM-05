package com.shopsmart.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StoreSettingsResponse(
        String storeName,
        String supportEmail,
        String currency,
        BigDecimal taxRate,
        boolean pricesIncludeTax,
        BigDecimal shippingFee,
        BigDecimal freeShippingThreshold,
        int defaultLowStockThreshold,
        boolean codEnabled,
        BigDecimal codMaxOrderAmount,
        boolean razorpayEnabled,
        boolean razorpayConfigured,
        int pendingPaymentTimeoutMinutes,
        Instant updatedAt) {
}

package com.shopsmart.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SalesAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        String granularity,
        BigDecimal revenue,
        long orders,
        long units,
        BigDecimal averageOrderValue,
        Double revenueChange,
        List<SeriesPoint> series,
        Map<String, Long> orderStatusDistribution,
        List<PaymentMethodShare> paymentMethods) {

    public record PaymentMethodShare(String method, long orders, BigDecimal revenue) {
    }
}

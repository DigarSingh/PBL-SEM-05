package com.shopsmart.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerAnalyticsResponse(LocalDate from, LocalDate to, long totalCustomers, long newCustomers,
                                        long purchasingCustomers, Double repeatPurchaseRate,
                                        List<DailyCount> newCustomersByDay, List<TopCustomer> topCustomers) {

    public record DailyCount(LocalDate day, long count) {
    }

    public record TopCustomer(Long userId, String name, String email, long orders, BigDecimal spent) {
    }
}

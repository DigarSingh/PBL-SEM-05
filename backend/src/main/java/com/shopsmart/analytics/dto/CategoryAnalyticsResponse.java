package com.shopsmart.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CategoryAnalyticsResponse(LocalDate from, LocalDate to, List<CategoryRow> categories) {

    public record CategoryRow(Long categoryId, String name, long units, BigDecimal revenue, long orders,
                              double revenueShare) {
    }
}

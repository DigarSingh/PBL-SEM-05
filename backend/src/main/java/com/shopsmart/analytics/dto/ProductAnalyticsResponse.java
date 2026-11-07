package com.shopsmart.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductAnalyticsResponse(LocalDate from, LocalDate to, List<ProductRow> topProducts,
                                       List<ProductRow> slowMoving) {

    /** conversionRate = units sold / product-page views in the period (null without views). */
    public record ProductRow(Long productId, String name, long units, BigDecimal revenue, long views,
                             Double conversionRate, Integer available) {
    }
}

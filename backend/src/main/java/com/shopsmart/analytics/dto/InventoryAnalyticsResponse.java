package com.shopsmart.analytics.dto;

import java.util.List;

/** Historical stock velocity (last 30 days) and resulting days of cover per product. */
public record InventoryAnalyticsResponse(List<Row> products) {

    public record Row(Long productId, String name, int available, long unitsLast30Days, double dailyVelocity,
                      Double daysOfCover, String status) {
    }
}

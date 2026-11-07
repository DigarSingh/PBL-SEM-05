package com.shopsmart.analytics.dto;

import com.shopsmart.inventory.dto.InventoryItemResponse;
import com.shopsmart.order.dto.AdminOrderSummaryResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        Totals totals,
        Comparison last30Days,
        List<SeriesPoint> salesLast30Days,
        Map<String, Long> orderStatusDistribution,
        List<AdminOrderSummaryResponse> recentOrders,
        List<InventoryItemResponse> lowStock) {

    public record Totals(BigDecimal totalSales, long orders, long customers, long products, long lowStockProducts,
                         long pendingOrders, long completedOrders) {
    }

    /** Current 30 days vs the previous 30; change values are percentages (null when not comparable). */
    public record Comparison(BigDecimal revenue, Double revenueChange, long orders, Double ordersChange,
                             BigDecimal averageOrderValue, Double averageOrderValueChange, long newCustomers,
                             Double newCustomersChange) {
    }
}

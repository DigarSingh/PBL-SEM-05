package com.shopsmart.analytics;

import com.shopsmart.analytics.dto.CategoryAnalyticsResponse;
import com.shopsmart.analytics.dto.CustomerAnalyticsResponse;
import com.shopsmart.analytics.dto.DashboardResponse;
import com.shopsmart.analytics.dto.InsightsResponse;
import com.shopsmart.analytics.dto.InventoryAnalyticsResponse;
import com.shopsmart.analytics.dto.ProductAnalyticsResponse;
import com.shopsmart.analytics.dto.SalesAnalyticsResponse;
import com.shopsmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: Analytics", description = "Dashboard, sales/product/category/customer analytics, insights and forecasts")
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analytics;
    private final InsightsService insights;
    private final Clock clock;

    public AdminAnalyticsController(AnalyticsService analytics, InsightsService insights, Clock clock) {
        this.analytics = analytics;
        this.insights = insights;
        this.clock = clock;
    }

    @Operation(summary = "Dashboard KPIs, 30-day comparison, sales trend, recent orders and low stock")
    @GetMapping("/api/admin/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(analytics.dashboard());
    }

    @Operation(summary = "Sales over time (granularity: day, week, month); dates are IST, inclusive")
    @GetMapping("/api/admin/analytics/sales")
    public ApiResponse<SalesAnalyticsResponse> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String granularity) {
        return ApiResponse.ok(analytics.sales(Period.of(from, to, clock, 30), granularity));
    }

    @Operation(summary = "Top-selling and slow-moving products with conversion rates")
    @GetMapping("/api/admin/analytics/products")
    public ApiResponse<ProductAnalyticsResponse> products(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ApiResponse.ok(analytics.products(Period.of(from, to, clock, 30), limit));
    }

    @Operation(summary = "Revenue, units and orders by category")
    @GetMapping("/api/admin/analytics/categories")
    public ApiResponse<CategoryAnalyticsResponse> categories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analytics.categories(Period.of(from, to, clock, 30)));
    }

    @Operation(summary = "Customer acquisition, repeat purchase rate and top customers")
    @GetMapping("/api/admin/analytics/customers")
    public ApiResponse<CustomerAnalyticsResponse> customers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analytics.customers(Period.of(from, to, clock, 30)));
    }

    @Operation(summary = "Stock velocity and days of cover per product (historical)")
    @GetMapping("/api/admin/analytics/inventory")
    public ApiResponse<InventoryAnalyticsResponse> inventory() {
        return ApiResponse.ok(analytics.inventory());
    }

    @Operation(summary = "Insights: historical observations plus separately labelled ML demand forecasts")
    @GetMapping("/api/admin/analytics/insights")
    public ApiResponse<InsightsResponse> insights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(insights.insights(Period.of(from, to, clock, 30)));
    }
}

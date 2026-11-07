package com.shopsmart.analytics;

import com.shopsmart.analytics.dto.CategoryAnalyticsResponse;
import com.shopsmart.analytics.dto.CustomerAnalyticsResponse;
import com.shopsmart.analytics.dto.DashboardResponse;
import com.shopsmart.analytics.dto.InventoryAnalyticsResponse;
import com.shopsmart.analytics.dto.ProductAnalyticsResponse;
import com.shopsmart.analytics.dto.SalesAnalyticsResponse;
import com.shopsmart.analytics.dto.SeriesPoint;
import com.shopsmart.common.util.BusinessTime;
import com.shopsmart.common.util.Money;
import com.shopsmart.inventory.InventoryRepository;
import com.shopsmart.inventory.InventoryService;
import com.shopsmart.order.OrderQueryService;
import com.shopsmart.order.OrderStatus;
import com.shopsmart.order.OrderRepository;
import com.shopsmart.product.ProductRepository;
import com.shopsmart.product.ProductStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historical metrics computed from recorded orders (no predictions here). */
@Service
public class AnalyticsService {

    private final AnalyticsRepository repository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final OrderQueryService orderQueryService;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, OrderRepository orderRepository,
                            ProductRepository productRepository, InventoryRepository inventoryRepository,
                            InventoryService inventoryService, OrderQueryService orderQueryService, Clock clock) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.orderQueryService = orderQueryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Period last30 = Period.of(null, null, clock, 30);
        Period previous = last30.previous();
        Instant epoch = Instant.EPOCH;
        Instant now = clock.instant();

        AnalyticsRepository.Totals allTime = repository.totals(epoch, now.plusSeconds(60));
        long pending = orderRepository.countByStatus(OrderStatus.PLACED) + orderRepository.countByStatus(OrderStatus.CONFIRMED)
                + orderRepository.countByStatus(OrderStatus.PROCESSING);
        DashboardResponse.Totals totals = new DashboardResponse.Totals(Money.of(allTime.revenue()), allTime.orders(),
                repository.countCustomers(), productRepository.countByStatus(ProductStatus.ACTIVE),
                inventoryRepository.countLowStock(), pending, orderRepository.countByStatus(OrderStatus.DELIVERED));

        AnalyticsRepository.Totals current = repository.totals(last30.startInstant(), last30.endInstant());
        AnalyticsRepository.Totals before = repository.totals(previous.startInstant(), previous.endInstant());
        long newCustomers = repository.countCustomersCreated(last30.startInstant(), last30.endInstant());
        long newCustomersBefore = repository.countCustomersCreated(previous.startInstant(), previous.endInstant());
        BigDecimal aov = average(current.revenue(), current.orders());
        BigDecimal aovBefore = average(before.revenue(), before.orders());
        DashboardResponse.Comparison comparison = new DashboardResponse.Comparison(Money.of(current.revenue()),
                change(current.revenue(), before.revenue()), current.orders(),
                change(BigDecimal.valueOf(current.orders()), BigDecimal.valueOf(before.orders())), aov,
                change(aov, aovBefore), newCustomers,
                change(BigDecimal.valueOf(newCustomers), BigDecimal.valueOf(newCustomersBefore)));

        List<SeriesPoint> series = fillDaily(repository.dailySales(last30.startInstant(), last30.endInstant()), last30);
        return new DashboardResponse(totals, comparison, series, repository.statusDistribution(epoch, now.plusSeconds(60)),
                orderQueryService.adminOrders(null, null, null, false, null, null, null, 0, 6).items(),
                inventoryRepository.findLowStock(PageRequest.of(0, 6)).stream()
                        .map(inventory -> inventoryService.toResponse(inventory)).toList());
    }

    @Transactional(readOnly = true)
    public SalesAnalyticsResponse sales(Period period, String granularity) {
        List<SeriesPoint> daily = fillDaily(repository.dailySales(period.startInstant(), period.endInstant()), period);
        String unit = granularity == null ? "day" : granularity.toLowerCase();
        List<SeriesPoint> series = switch (unit) {
            case "week" -> bucket(daily, date -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            case "month" -> bucket(daily, date -> date.withDayOfMonth(1));
            default -> daily;
        };
        AnalyticsRepository.Totals totals = repository.totals(period.startInstant(), period.endInstant());
        Period previous = period.previous();
        AnalyticsRepository.Totals before = repository.totals(previous.startInstant(), previous.endInstant());
        List<SalesAnalyticsResponse.PaymentMethodShare> methods = new ArrayList<>();
        repository.paymentMethodSplit(period.startInstant(), period.endInstant()).forEach((method, values) ->
                methods.add(new SalesAnalyticsResponse.PaymentMethodShare(method, values[0].longValue(),
                        Money.of(values[1]))));
        return new SalesAnalyticsResponse(period.from(), period.to(), unit.equals("week") || unit.equals("month") ? unit : "day",
                Money.of(totals.revenue()), totals.orders(), totals.units(), average(totals.revenue(), totals.orders()),
                change(totals.revenue(), before.revenue()), series,
                repository.statusDistribution(period.startInstant(), period.endInstant()), methods);
    }

    @Transactional(readOnly = true)
    public ProductAnalyticsResponse products(Period period, int limit) {
        Map<Long, Long> views = repository.viewsByProduct(period.startInstant(), period.endInstant());
        Map<Long, int[]> stock = repository.stockByProduct();
        List<ProductAnalyticsResponse.ProductRow> top = repository.productSales(period.startInstant(),
                period.endInstant(), limit).stream()
                .map(row -> row(row.productId(), row.name(), row.units(), row.revenue(), views, stock))
                .toList();

        // Slow movers: in stock (>= 10 available) but at most one unit sold in the last 60 days.
        Instant now = clock.instant();
        Map<Long, Long> recentUnits = repository.unitsByProduct(now.minus(java.time.Duration.ofDays(60)), now);
        Map<Long, String> names = repository.activeProductNames();
        List<ProductAnalyticsResponse.ProductRow> slow = stock.entrySet().stream()
                .filter(entry -> entry.getValue()[0] >= 10 && recentUnits.getOrDefault(entry.getKey(), 0L) <= 1)
                .filter(entry -> names.containsKey(entry.getKey()))
                .sorted(Map.Entry.<Long, int[]>comparingByValue(Comparator.comparingInt(v -> -v[0])))
                .limit(limit)
                .map(entry -> row(entry.getKey(), names.get(entry.getKey()), recentUnits.getOrDefault(entry.getKey(), 0L),
                        BigDecimal.ZERO, views, stock))
                .toList();
        return new ProductAnalyticsResponse(period.from(), period.to(), top, slow);
    }

    @Transactional(readOnly = true)
    public CategoryAnalyticsResponse categories(Period period) {
        List<AnalyticsRepository.CategorySales> rows = repository.categorySales(period.startInstant(), period.endInstant());
        BigDecimal total = rows.stream().map(AnalyticsRepository.CategorySales::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CategoryAnalyticsResponse(period.from(), period.to(), rows.stream()
                .map(row -> new CategoryAnalyticsResponse.CategoryRow(row.categoryId(), row.name(), row.units(),
                        Money.of(row.revenue()), row.orders(), total.signum() == 0 ? 0
                        : row.revenue().divide(total, 4, RoundingMode.HALF_UP).doubleValue()))
                .toList());
    }

    @Transactional(readOnly = true)
    public CustomerAnalyticsResponse customers(Period period) {
        Map<LocalDate, Long> daily = repository.newCustomersByDay(period.startInstant(), period.endInstant());
        List<CustomerAnalyticsResponse.DailyCount> newByDay = new ArrayList<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            newByDay.add(new CustomerAnalyticsResponse.DailyCount(day, daily.getOrDefault(day, 0L)));
        }
        long[] repeat = repository.repeatCustomers(period.startInstant(), period.endInstant());
        return new CustomerAnalyticsResponse(period.from(), period.to(), repository.countCustomers(),
                daily.values().stream().mapToLong(Long::longValue).sum(), repeat[0],
                repeat[0] == 0 ? null : Math.round(repeat[1] * 1000.0 / repeat[0]) / 10.0, newByDay,
                repository.topCustomers(period.startInstant(), period.endInstant(), 10).stream()
                        .map(c -> new CustomerAnalyticsResponse.TopCustomer(c.userId(), c.name(), c.email(), c.orders(),
                                Money.of(c.spent())))
                        .toList());
    }

    @Transactional(readOnly = true)
    public InventoryAnalyticsResponse inventory() {
        Instant now = clock.instant();
        Map<Long, Long> units = repository.unitsByProduct(now.minus(java.time.Duration.ofDays(30)), now);
        Map<Long, String> names = repository.activeProductNames();
        List<InventoryAnalyticsResponse.Row> rows = new ArrayList<>();
        repository.stockByProduct().forEach((productId, stock) -> {
            if (!names.containsKey(productId)) {
                return;
            }
            long sold = units.getOrDefault(productId, 0L);
            double velocity = sold / 30.0;
            Double cover = velocity > 0 ? Math.round(stock[0] / velocity * 10) / 10.0 : null;
            String status = stock[0] == 0 ? "OUT_OF_STOCK"
                    : cover != null && cover < 7 ? "CRITICAL"
                    : stock[0] <= stock[1] || (cover != null && cover < 14) ? "LOW"
                    : sold <= 1 && stock[0] >= 10 ? "SLOW_MOVING" : "HEALTHY";
            rows.add(new InventoryAnalyticsResponse.Row(productId, names.get(productId), stock[0], sold,
                    Math.round(velocity * 100) / 100.0, cover, status));
        });
        rows.sort(Comparator.comparing((InventoryAnalyticsResponse.Row row) -> row.daysOfCover() == null
                ? Double.MAX_VALUE : row.daysOfCover()).thenComparing(InventoryAnalyticsResponse.Row::name));
        return new InventoryAnalyticsResponse(rows);
    }

    private static ProductAnalyticsResponse.ProductRow row(Long productId, String name, long units, BigDecimal revenue,
                                                          Map<Long, Long> views, Map<Long, int[]> stock) {
        long viewCount = views.getOrDefault(productId, 0L);
        int[] productStock = stock.get(productId);
        return new ProductAnalyticsResponse.ProductRow(productId, name, units, Money.of(revenue), viewCount,
                viewCount == 0 ? null : Math.round(units * 1000.0 / viewCount) / 10.0,
                productStock == null ? null : productStock[0]);
    }

    static List<SeriesPoint> fillDaily(List<AnalyticsRepository.DailySales> rows, Period period) {
        Map<LocalDate, AnalyticsRepository.DailySales> byDay = new LinkedHashMap<>();
        rows.forEach(row -> byDay.put(row.day(), row));
        List<SeriesPoint> series = new ArrayList<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            AnalyticsRepository.DailySales row = byDay.get(day);
            series.add(row == null ? new SeriesPoint(day, Money.ZERO, 0, 0)
                    : new SeriesPoint(day, Money.of(row.revenue()), row.orders(), row.units()));
        }
        return series;
    }

    private static List<SeriesPoint> bucket(List<SeriesPoint> daily, java.util.function.UnaryOperator<LocalDate> key) {
        Map<LocalDate, SeriesPoint> buckets = new LinkedHashMap<>();
        for (SeriesPoint point : daily) {
            buckets.merge(key.apply(point.period()), point, (a, b) -> new SeriesPoint(a.period(),
                    a.revenue().add(b.revenue()), a.orders() + b.orders(), a.units() + b.units()));
        }
        return buckets.entrySet().stream()
                .map(e -> new SeriesPoint(e.getKey(), e.getValue().revenue(), e.getValue().orders(), e.getValue().units()))
                .toList();
    }

    static BigDecimal average(BigDecimal revenue, long orders) {
        return orders == 0 ? Money.ZERO : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }

    /** Percentage change, or null when there is no baseline to compare against. */
    static Double change(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP).doubleValue();
    }

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(BusinessTime.ZONE));
    }
}

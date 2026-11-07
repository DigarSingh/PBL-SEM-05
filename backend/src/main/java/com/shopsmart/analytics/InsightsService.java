package com.shopsmart.analytics;

import com.shopsmart.ai.AiServiceClient;
import com.shopsmart.analytics.dto.InsightsResponse;
import com.shopsmart.common.util.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Admin insights in two explicitly separated parts:
 * <ul>
 *   <li>historical - rule-based observations over recorded sales (always available);</li>
 *   <li>predictions - demand forecasts from the AI service's model, returned with an explicit
 *       status; the model reports "insufficient data" instead of guessing.</li>
 * </ul>
 */
@Service
public class InsightsService {

    static final double SIGNIFICANT_CHANGE = 0.30;
    static final int MIN_UNITS_FOR_TREND = 5;
    static final int FORECAST_HISTORY_DAYS = 120;
    static final int HORIZON_DAYS = 14;
    static final int LEAD_TIME_DAYS = 7;
    static final int MAX_FORECAST_PRODUCTS = 30;

    private final AnalyticsRepository repository;
    private final AiServiceClient aiClient;
    private final Clock clock;

    public InsightsService(AnalyticsRepository repository, AiServiceClient aiClient, Clock clock) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InsightsResponse insights(Period period) {
        return new InsightsResponse(period.from(), period.to(), historical(period), predictions());
    }

    List<InsightsResponse.Insight> historical(Period period) {
        Map<Long, Long> current = repository.unitsByProduct(period.startInstant(), period.endInstant());
        Period previous = period.previous();
        Map<Long, Long> before = repository.unitsByProduct(previous.startInstant(), previous.endInstant());
        Map<Long, String> names = repository.activeProductNames();
        List<InsightsResponse.Insight> rising = new ArrayList<>();
        List<InsightsResponse.Insight> declining = new ArrayList<>();

        Set<Long> products = new HashSet<>(current.keySet());
        products.addAll(before.keySet());
        for (Long productId : products) {
            String name = names.get(productId);
            if (name == null) {
                continue;
            }
            long now = current.getOrDefault(productId, 0L);
            long then = before.getOrDefault(productId, 0L);
            if (now >= MIN_UNITS_FOR_TREND && (then == 0 || (now - then) / (double) then >= SIGNIFICANT_CHANGE)) {
                double growth = then == 0 ? 1.0 : (now - then) / (double) then;
                rising.add(new InsightsResponse.Insight("RISING_SALES", "positive", name + " is selling faster",
                        then == 0 ? name + " sold " + now + " units in the selected period, up from none in the previous period."
                                : name + " had increased sales during the selected period (+" + Math.round(growth * 100)
                                + "%: " + now + " units vs " + then + ").",
                        productId, BigDecimal.valueOf(Math.round(growth * 100))));
            } else if (then >= MIN_UNITS_FOR_TREND && (then - now) / (double) then >= SIGNIFICANT_CHANGE) {
                double drop = (then - now) / (double) then;
                declining.add(new InsightsResponse.Insight("DECLINING_SALES", "warning", name + " is slowing down",
                        name + " has declining sales (-" + Math.round(drop * 100) + "%: " + now + " units vs " + then
                                + " in the previous period).",
                        productId, BigDecimal.valueOf(-Math.round(drop * 100))));
            }
        }
        rising.sort(Comparator.comparing(InsightsResponse.Insight::metric).reversed());
        declining.sort(Comparator.comparing(InsightsResponse.Insight::metric));

        // Stock relative to the recent sales rate (last 30 days).
        Instant nowInstant = clock.instant();
        Map<Long, Long> last30 = repository.unitsByProduct(nowInstant.minus(Duration.ofDays(30)), nowInstant);
        List<InsightsResponse.Insight> stock = new ArrayList<>();
        repository.stockByProduct().forEach((productId, levels) -> {
            String name = names.get(productId);
            long sold = last30.getOrDefault(productId, 0L);
            if (name == null || sold == 0) {
                return;
            }
            double perDay = sold / 30.0;
            double cover = levels[0] / perDay;
            if (cover < 14) {
                stock.add(new InsightsResponse.Insight("LOW_STOCK_VS_SALES", cover < 7 ? "critical" : "warning",
                        name + " may run out soon",
                        name + " has low stock relative to recent sales: " + levels[0] + " left at ~"
                                + Math.round(perDay * 10) / 10.0 + " units/day (about " + Math.round(cover)
                                + " days of cover).",
                        productId, BigDecimal.valueOf(Math.round(cover * 10) / 10.0)));
            }
        });
        stock.sort(Comparator.comparing(InsightsResponse.Insight::metric));

        List<InsightsResponse.Insight> result = new ArrayList<>();
        result.addAll(stock.stream().limit(5).toList());
        result.addAll(rising.stream().limit(5).toList());
        result.addAll(declining.stream().limit(5).toList());
        List<AnalyticsRepository.CategorySales> categories = repository.categorySales(period.startInstant(),
                period.endInstant());
        if (!categories.isEmpty()) {
            BigDecimal total = categories.stream().map(AnalyticsRepository.CategorySales::revenue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            AnalyticsRepository.CategorySales top = categories.getFirst();
            long share = total.signum() == 0 ? 0 : top.revenue().multiply(BigDecimal.valueOf(100))
                    .divide(total, 0, java.math.RoundingMode.HALF_UP).longValue();
            result.add(new InsightsResponse.Insight("TOP_CATEGORY", "info", top.name() + " leads revenue",
                    top.name() + " generated the most revenue in the selected period ("
                            + com.shopsmart.coupon.CouponRules.format(Money.of(top.revenue())) + ", " + share
                            + "% of sales).", null, BigDecimal.valueOf(share)));
        }
        if (result.isEmpty()) {
            result.add(new InsightsResponse.Insight("INSUFFICIENT_DATA", "info", "Insufficient data",
                    "There are not enough recorded sales in this period to derive insights.", null, null));
        }
        return result;
    }

    InsightsResponse.Predictions predictions() {
        Instant now = clock.instant();
        Instant historyStart = now.minus(Duration.ofDays(FORECAST_HISTORY_DAYS));
        Map<Long, Map<LocalDate, Long>> daily = repository.dailyUnitsByProduct(historyStart, now);
        Map<Long, String> names = repository.activeProductNames();
        Map<Long, int[]> stock = repository.stockByProduct();
        List<Map.Entry<Long, Map<LocalDate, Long>>> ranked = daily.entrySet().stream()
                .filter(entry -> names.containsKey(entry.getKey()))
                .sorted(Comparator.comparingLong((Map.Entry<Long, Map<LocalDate, Long>> e) ->
                        e.getValue().values().stream().mapToLong(Long::longValue).sum()).reversed())
                .limit(MAX_FORECAST_PRODUCTS)
                .toList();
        if (ranked.isEmpty()) {
            return new InsightsResponse.Predictions(false, "INSUFFICIENT_DATA", null, HORIZON_DAYS, LEAD_TIME_DAYS, now,
                    "Insufficient data: no recorded sales in the last " + FORECAST_HISTORY_DAYS + " days.", List.of());
        }
        LocalDate end = AnalyticsService.today(clock);
        List<Map<String, Object>> series = new ArrayList<>();
        for (Map.Entry<Long, Map<LocalDate, Long>> entry : ranked) {
            List<Map<String, Object>> points = new ArrayList<>();
            entry.getValue().forEach((day, units) -> points.add(Map.of("date", day.toString(), "units", units)));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("product_id", entry.getKey());
            item.put("name", names.get(entry.getKey()));
            item.put("available", stock.getOrDefault(entry.getKey(), new int[] {0, 0})[0]);
            item.put("daily", points);
            series.add(item);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("history_start", end.minusDays(FORECAST_HISTORY_DAYS - 1L).toString());
        request.put("history_end", end.toString());
        request.put("horizon_days", HORIZON_DAYS);
        request.put("lead_time_days", LEAD_TIME_DAYS);
        request.put("series", series);

        Optional<JsonNode> response = aiClient.forecast(request);
        if (response.isEmpty()) {
            return new InsightsResponse.Predictions(false, "UNAVAILABLE", null, HORIZON_DAYS, LEAD_TIME_DAYS, now,
                    "The forecasting service is unavailable. Historical insights above are unaffected.", List.of());
        }
        JsonNode body = response.get();
        List<InsightsResponse.Forecast> items = new ArrayList<>();
        for (JsonNode item : body.path("forecasts")) {
            items.add(new InsightsResponse.Forecast(item.path("product_id").asLong(), item.path("name").asString(null),
                    item.path("status").asString("UNKNOWN"), number(item, "predicted_units"), number(item, "lower"),
                    number(item, "upper"), number(item, "daily_rate"), item.path("trend").asString(null),
                    item.path("available").isNumber() ? item.path("available").asInt() : null,
                    number(item, "days_of_cover"),
                    item.path("restock_quantity").isNumber() ? item.path("restock_quantity").asInt() : null,
                    item.path("note").asString(null)));
        }
        boolean anyForecast = items.stream().anyMatch(item -> "OK".equals(item.status()));
        return new InsightsResponse.Predictions(true, anyForecast ? "OK" : "INSUFFICIENT_DATA",
                body.path("model").asString(null), HORIZON_DAYS, LEAD_TIME_DAYS, now,
                anyForecast ? "Forecasts are statistical estimates from past sales, not guarantees."
                        : "Insufficient data to forecast demand reliably yet.", items);
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }
}

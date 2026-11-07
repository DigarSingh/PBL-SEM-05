package com.shopsmart.analytics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Read-only aggregate SQL for dashboards and analytics. "Sales" are orders that were placed and
 * not cancelled (unpaid online orders are excluded); days are bucketed in IST.
 */
@Repository
public class AnalyticsRepository {

    private static final String COUNTED = "o.placed_at is not null and o.status not in ('CANCELLED', 'PENDING_PAYMENT')";
    private static final String IST_DAY = "date(convert_tz(%s, '+00:00', '+05:30'))";

    @PersistenceContext
    private EntityManager entityManager;

    public record DailySales(LocalDate day, BigDecimal revenue, long orders, long units) {
    }

    public record Totals(BigDecimal revenue, long orders, long units, long customers) {
    }

    public record ProductSales(Long productId, String name, long units, BigDecimal revenue) {
    }

    public record CategorySales(Long categoryId, String name, long units, BigDecimal revenue, long orders) {
    }

    public record CustomerSpend(Long userId, String name, String email, long orders, BigDecimal spent) {
    }

    @SuppressWarnings("unchecked")
    public List<DailySales> dailySales(Instant from, Instant to) {
        String day = IST_DAY.formatted("o.placed_at");
        Map<LocalDate, Object[]> totals = new LinkedHashMap<>();
        for (Object[] row : (List<Object[]>) native_("select " + day + " d, sum(o.total_amount), count(*) from orders o "
                + "where " + COUNTED + " and o.placed_at >= :from and o.placed_at < :to group by d order by d", from, to)) {
            totals.put(toDate(row[0]), row);
        }
        Map<LocalDate, Long> units = new HashMap<>();
        for (Object[] row : (List<Object[]>) native_("select " + day + " d, sum(oi.quantity) from order_items oi "
                + "join orders o on o.id = oi.order_id where " + COUNTED
                + " and o.placed_at >= :from and o.placed_at < :to group by d", from, to)) {
            units.put(toDate(row[0]), ((Number) row[1]).longValue());
        }
        List<DailySales> result = new ArrayList<>();
        totals.forEach((date, row) -> result.add(new DailySales(date, (BigDecimal) row[1], ((Number) row[2]).longValue(),
                units.getOrDefault(date, 0L))));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Totals totals(Instant from, Instant to) {
        Object[] row = (Object[]) native_("select coalesce(sum(o.total_amount), 0), count(*), count(distinct o.user_id) "
                + "from orders o where " + COUNTED + " and o.placed_at >= :from and o.placed_at < :to", from, to).getFirst();
        Number units = (Number) ((List<Object>) native_("select coalesce(sum(oi.quantity), 0) from order_items oi "
                + "join orders o on o.id = oi.order_id where " + COUNTED
                + " and o.placed_at >= :from and o.placed_at < :to", from, to)).getFirst();
        return new Totals((BigDecimal) row[0], ((Number) row[1]).longValue(), units.longValue(),
                ((Number) row[2]).longValue());
    }

    @SuppressWarnings("unchecked")
    public List<ProductSales> productSales(Instant from, Instant to, int limit) {
        List<Object[]> rows = entityManager.createNativeQuery("select oi.product_id, max(oi.product_name), "
                        + "sum(oi.quantity) units, sum(oi.line_total) revenue from order_items oi "
                        + "join orders o on o.id = oi.order_id where " + COUNTED
                        + " and o.placed_at >= :from and o.placed_at < :to "
                        + "group by oi.product_id order by revenue desc limit :limit")
                .setParameter("from", Timestamp.from(from)).setParameter("to", Timestamp.from(to))
                .setParameter("limit", limit).getResultList();
        return rows.stream().map(r -> new ProductSales(((Number) r[0]).longValue(), (String) r[1],
                ((Number) r[2]).longValue(), (BigDecimal) r[3])).toList();
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Long> unitsByProduct(Instant from, Instant to) {
        Map<Long, Long> units = new HashMap<>();
        for (Object[] row : (List<Object[]>) native_("select oi.product_id, sum(oi.quantity) from order_items oi "
                + "join orders o on o.id = oi.order_id where " + COUNTED
                + " and o.placed_at >= :from and o.placed_at < :to group by oi.product_id", from, to)) {
            units.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return units;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, Long> viewsByProduct(Instant from, Instant to) {
        Map<Long, Long> views = new HashMap<>();
        for (Object[] row : (List<Object[]>) native_("select v.product_id, count(*) from product_views v "
                + "where v.created_at >= :from and v.created_at < :to group by v.product_id", from, to)) {
            views.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return views;
    }

    /** Daily units per product (for forecasting). */
    @SuppressWarnings("unchecked")
    public Map<Long, Map<LocalDate, Long>> dailyUnitsByProduct(Instant from, Instant to) {
        Map<Long, Map<LocalDate, Long>> series = new HashMap<>();
        for (Object[] row : (List<Object[]>) native_("select oi.product_id, " + IST_DAY.formatted("o.placed_at")
                + " d, sum(oi.quantity) from order_items oi join orders o on o.id = oi.order_id where " + COUNTED
                + " and o.placed_at >= :from and o.placed_at < :to group by oi.product_id, d", from, to)) {
            series.computeIfAbsent(((Number) row[0]).longValue(), key -> new LinkedHashMap<>())
                    .put(toDate(row[1]), ((Number) row[2]).longValue());
        }
        return series;
    }

    @SuppressWarnings("unchecked")
    public List<CategorySales> categorySales(Instant from, Instant to) {
        List<Object[]> rows = native_("select c.id, c.name, sum(oi.quantity), sum(oi.line_total) revenue, "
                + "count(distinct o.id) from order_items oi join orders o on o.id = oi.order_id "
                + "join products p on p.id = oi.product_id join categories c on c.id = p.category_id where " + COUNTED
                + " and o.placed_at >= :from and o.placed_at < :to group by c.id, c.name order by revenue desc", from, to);
        return rows.stream().map(r -> new CategorySales(((Number) r[0]).longValue(), (String) r[1],
                ((Number) r[2]).longValue(), (BigDecimal) r[3], ((Number) r[4]).longValue())).toList();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Long> statusDistribution(Instant from, Instant to) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (Object[] row : (List<Object[]>) native_("select o.status, count(*) from orders o "
                + "where o.status <> 'PENDING_PAYMENT' and o.created_at >= :from and o.created_at < :to "
                + "group by o.status", from, to)) {
            distribution.put((String) row[0], ((Number) row[1]).longValue());
        }
        return distribution;
    }

    @SuppressWarnings("unchecked")
    public Map<String, BigDecimal[]> paymentMethodSplit(Instant from, Instant to) {
        Map<String, BigDecimal[]> split = new LinkedHashMap<>();
        for (Object[] row : (List<Object[]>) native_("select o.payment_method, count(*), sum(o.total_amount) from orders o "
                + "where " + COUNTED + " and o.placed_at >= :from and o.placed_at < :to group by o.payment_method", from, to)) {
            split.put((String) row[0], new BigDecimal[] {new BigDecimal(((Number) row[1]).longValue()), (BigDecimal) row[2]});
        }
        return split;
    }

    @SuppressWarnings("unchecked")
    public Map<LocalDate, Long> newCustomersByDay(Instant from, Instant to) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (Object[] row : (List<Object[]>) native_("select " + IST_DAY.formatted("u.created_at") + " d, count(*) "
                + "from users u join user_roles ur on ur.user_id = u.id join roles r on r.id = ur.role_id "
                + "where r.name = 'CUSTOMER' and u.created_at >= :from and u.created_at < :to group by d order by d",
                from, to)) {
            counts.put(toDate(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    public List<CustomerSpend> topCustomers(Instant from, Instant to, int limit) {
        List<Object[]> rows = entityManager.createNativeQuery("select u.id, u.full_name, u.email, count(o.id), "
                        + "sum(o.total_amount) spent from orders o join users u on u.id = o.user_id where " + COUNTED
                        + " and o.placed_at >= :from and o.placed_at < :to group by u.id, u.full_name, u.email "
                        + "order by spent desc limit :limit")
                .setParameter("from", Timestamp.from(from)).setParameter("to", Timestamp.from(to))
                .setParameter("limit", limit).getResultList();
        return rows.stream().map(r -> new CustomerSpend(((Number) r[0]).longValue(), (String) r[1], (String) r[2],
                ((Number) r[3]).longValue(), (BigDecimal) r[4])).toList();
    }

    /** [customers with at least one order, customers with two or more] in the period. */
    @SuppressWarnings("unchecked")
    public long[] repeatCustomers(Instant from, Instant to) {
        Object[] row = (Object[]) native_("select count(*), coalesce(sum(case when n >= 2 then 1 else 0 end), 0) from "
                + "(select o.user_id, count(*) n from orders o where " + COUNTED
                + " and o.placed_at >= :from and o.placed_at < :to group by o.user_id) t", from, to).getFirst();
        return new long[] {((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
    }

    public long countCustomers() {
        return ((Number) entityManager.createNativeQuery("select count(*) from users u join user_roles ur "
                + "on ur.user_id = u.id join roles r on r.id = ur.role_id where r.name = 'CUSTOMER'")
                .getSingleResult()).longValue();
    }

    public long countCustomersCreated(Instant from, Instant to) {
        return ((Number) entityManager.createNativeQuery("select count(*) from users u join user_roles ur "
                        + "on ur.user_id = u.id join roles r on r.id = ur.role_id where r.name = 'CUSTOMER' "
                        + "and u.created_at >= :from and u.created_at < :to")
                .setParameter("from", Timestamp.from(from)).setParameter("to", Timestamp.from(to))
                .getSingleResult()).longValue();
    }

    /** Current stock per active product: {productId -> [available, lowStockThresholdSum]}. */
    @SuppressWarnings("unchecked")
    public Map<Long, int[]> stockByProduct() {
        Map<Long, int[]> stock = new HashMap<>();
        for (Object[] row : (List<Object[]>) entityManager.createNativeQuery("select v.product_id, "
                + "sum(greatest(i.quantity - i.reserved, 0)), sum(i.low_stock_threshold) from inventory i "
                + "join product_variants v on v.id = i.variant_id join products p on p.id = v.product_id "
                + "where v.active = true and p.status = 'ACTIVE' group by v.product_id").getResultList()) {
            stock.put(((Number) row[0]).longValue(), new int[] {((Number) row[1]).intValue(), ((Number) row[2]).intValue()});
        }
        return stock;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, String> activeProductNames() {
        Map<Long, String> names = new HashMap<>();
        for (Object[] row : (List<Object[]>) entityManager.createNativeQuery(
                "select id, name from products where status = 'ACTIVE'").getResultList()) {
            names.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return names;
    }

    @SuppressWarnings("rawtypes")
    private List native_(String sql, Instant from, Instant to) {
        return entityManager.createNativeQuery(sql)
                .setParameter("from", Timestamp.from(from))
                .setParameter("to", Timestamp.from(to))
                .getResultList();
    }

    private static LocalDate toDate(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return LocalDate.parse(String.valueOf(value));
    }
}

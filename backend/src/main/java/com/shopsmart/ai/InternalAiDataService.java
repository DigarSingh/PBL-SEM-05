package com.shopsmart.ai;

import com.shopsmart.product.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data the AI service pulls over the internal API (it never connects to the database):
 * a catalog snapshot for embeddings and aggregated, pseudonymous interaction weights for
 * collaborative filtering. No names, e-mails or addresses leave the backend.
 */
@Service
public class InternalAiDataService {

    private static final int MAX_DESCRIPTION = 1200;

    @PersistenceContext
    private EntityManager entityManager;

    private final Clock clock;

    public InternalAiDataService(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> catalog() {
        List<Object[]> rows = entityManager.createNativeQuery("""
                select p.id, p.name, b.name, c.name, c.id, b.id, p.short_description, p.description,
                       p.sale_price, p.average_rating, p.review_count, p.sold_count, p.updated_at,
                       coalesce((select sum(greatest(i.quantity - i.reserved, 0)) from inventory i
                                 join product_variants v on v.id = i.variant_id
                                 where v.product_id = p.id and v.active = true), 0) as available
                from products p join brands b on b.id = p.brand_id join categories c on c.id = p.category_id
                where p.status = :status and b.active = true and c.active = true
                order by p.id
                """).setParameter("status", ProductStatus.ACTIVE.name()).getResultList();
        Map<Long, List<String>> tags = new HashMap<>();
        for (Object[] row : (List<Object[]>) entityManager.createNativeQuery(
                "select product_id, tag from product_tags").getResultList()) {
            tags.computeIfAbsent(((Number) row[0]).longValue(), key -> new ArrayList<>()).add((String) row[1]);
        }
        Map<Long, List<String>> specs = new HashMap<>();
        for (Object[] row : (List<Object[]>) entityManager.createNativeQuery(
                "select product_id, concat(name, ': ', value) from product_specifications order by product_id, position")
                .getResultList()) {
            specs.computeIfAbsent(((Number) row[0]).longValue(), key -> new ArrayList<>()).add((String) row[1]);
        }
        List<Map<String, Object>> products = new ArrayList<>();
        Instant latest = Instant.EPOCH;
        for (Object[] row : rows) {
            long id = ((Number) row[0]).longValue();
            Instant updated = toInstant(row[12]);
            if (updated.isAfter(latest)) {
                latest = updated;
            }
            String description = row[7] == null ? "" : (String) row[7];
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("id", id);
            product.put("name", row[1]);
            product.put("brand", row[2]);
            product.put("category", row[3]);
            product.put("category_id", ((Number) row[4]).longValue());
            product.put("brand_id", ((Number) row[5]).longValue());
            product.put("short_description", row[6] == null ? "" : row[6]);
            product.put("description", description.length() > MAX_DESCRIPTION
                    ? description.substring(0, MAX_DESCRIPTION) : description);
            product.put("tags", tags.getOrDefault(id, List.of()));
            product.put("specifications", specs.getOrDefault(id, List.of()));
            product.put("price", ((BigDecimal) row[8]).doubleValue());
            product.put("rating", ((BigDecimal) row[9]).doubleValue());
            product.put("review_count", ((Number) row[10]).intValue());
            product.put("sold_count", ((Number) row[11]).intValue());
            product.put("available", ((Number) row[13]).intValue());
            products.add(product);
        }
        // Include all indexed fields in the fingerprint. Stock and sold_count can change through
        // bulk SQL updates without touching products.updated_at; omitting them leaves the AI's
        // in-stock mask and popularity prior stale indefinitely.
        String version = rows.size() + "-" + latest.toEpochMilli() + "-" + Integer.toHexString(products.hashCode());
        return Map.of("version", version, "generated_at", clock.instant().toString(),
                "products", products);
    }

    /** (user, product, weight) triples from purchases and behavioural events of the last {@code days} days. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> interactions(int days) {
        Timestamp since = Timestamp.from(clock.instant().minus(Duration.ofDays(days)));
        List<Object[]> rows = entityManager.createNativeQuery("""
                select user_id, product_id, sum(weight) from (
                    select o.user_id as user_id, oi.product_id as product_id, 5.0 * oi.quantity as weight
                    from order_items oi join orders o on o.id = oi.order_id
                    where o.placed_at >= :since and o.status not in ('CANCELLED', 'PENDING_PAYMENT')
                    union all
                    select a.user_id, a.product_id,
                           case a.event_type when 'PRODUCT_VIEW' then 1.0 when 'ADD_TO_CART' then 3.0
                                             when 'WISHLIST_ADD' then 3.0 when 'REVIEW' then 2.0 else 0 end
                    from user_activities a
                    where a.created_at >= :since and a.product_id is not null
                      and a.event_type in ('PRODUCT_VIEW', 'ADD_TO_CART', 'WISHLIST_ADD', 'REVIEW')
                ) signals
                group by user_id, product_id
                having sum(weight) > 0
                """).setParameter("since", since).getResultList();
        List<Map<String, Object>> interactions = rows.stream()
                .map(row -> Map.<String, Object>of("user_id", ((Number) row[0]).longValue(),
                        "product_id", ((Number) row[1]).longValue(), "weight", ((Number) row[2]).doubleValue()))
                .toList();
        return Map.of("days", days, "interactions", interactions);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime local) {
            return local.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return Instant.EPOCH;
    }
}

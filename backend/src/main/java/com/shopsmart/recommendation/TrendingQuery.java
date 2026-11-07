package com.shopsmart.recommendation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Momentum ranking over recent purchases, product views and wishlist saves. */
@Repository
public class TrendingQuery {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Long> trending(Instant since, int limit, List<Long> excludeIds) {
        List<Long> exclude = excludeIds.isEmpty() ? List.of(-1L) : excludeIds;
        List<Number> ids = entityManager.createNativeQuery("""
                select p.id from products p
                join brands b on b.id = p.brand_id and b.active = true
                join categories c on c.id = p.category_id and c.active = true
                left join (select oi.product_id, sum(oi.quantity) units from order_items oi
                           join orders o on o.id = oi.order_id
                           where o.placed_at >= :since and o.status not in ('CANCELLED', 'PENDING_PAYMENT')
                           group by oi.product_id) s on s.product_id = p.id
                left join (select product_id, count(*) views from product_views
                           where created_at >= :since group by product_id) v on v.product_id = p.id
                left join (select product_id, count(*) saves from user_activities
                           where event_type = 'WISHLIST_ADD' and created_at >= :since group by product_id) w
                       on w.product_id = p.id
                where p.status = 'ACTIVE' and p.id not in (:exclude)
                  and exists (select 1 from inventory i join product_variants pv on pv.id = i.variant_id
                              where pv.product_id = p.id and pv.active = true and i.quantity - i.reserved > 0)
                order by coalesce(s.units, 0) * 3 + coalesce(v.views, 0) * 0.2 + coalesce(w.saves, 0) desc,
                         p.sold_count desc, p.average_rating desc, p.id desc
                limit :limit
                """)
                .setParameter("since", Timestamp.from(since))
                .setParameter("exclude", exclude)
                .setParameter("limit", limit)
                .getResultList();
        return ids.stream().map(Number::longValue).toList();
    }
}

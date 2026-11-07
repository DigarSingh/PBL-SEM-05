package com.shopsmart.user;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read queries over users holding the CUSTOMER role. */
public interface CustomerRepository extends Repository<User, Long> {

    @Query(value = """
            select u from User u where exists (select r from u.roles r where r.name = 'CUSTOMER')
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.email) like :q or lower(u.fullName) like :q or u.phone like :q)
            """,
            countQuery = """
            select count(u) from User u where exists (select r from u.roles r where r.name = 'CUSTOMER')
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.email) like :q or lower(u.fullName) like :q or u.phone like :q)
            """)
    Page<User> search(@Param("q") String likePattern, @Param("status") UserStatus status, Pageable pageable);

    /** [userId, orders, totalSpent, lastPlacedAt] for placed, non-cancelled orders. */
    @Query("""
            select o.user.id, count(o), sum(o.totalAmount), max(o.placedAt) from Order o
            where o.user.id in :userIds and o.placedAt is not null
              and o.status <> com.shopsmart.order.OrderStatus.CANCELLED
            group by o.user.id
            """)
    List<Object[]> orderStats(@Param("userIds") Collection<Long> userIds);
}

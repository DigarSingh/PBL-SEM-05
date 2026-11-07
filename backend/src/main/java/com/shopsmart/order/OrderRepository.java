package com.shopsmart.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderNumber(String orderNumber);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("select o.user.id from Order o where o.id = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);

    @Query("select o from Order o join fetch o.user where o.id = :id")
    Optional<Order> findWithUser(@Param("id") Long id);

    @Query("select o from Order o where o.user.id = :userId and (:status is null or o.status = :status)")
    Page<Order> findForCustomer(@Param("userId") Long userId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("select o from Order o where o.user.id = :userId and o.status = com.shopsmart.order.OrderStatus.PENDING_PAYMENT order by o.createdAt desc")
    List<Order> findPendingPayment(@Param("userId") Long userId);

    @Query("""
            select o from Order o where o.status = com.shopsmart.order.OrderStatus.PENDING_PAYMENT
              and o.createdAt < :cutoff order by o.createdAt asc
            """)
    List<Order> findPendingPaymentCreatedBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    /** Delivered orders of the user that contain the product (verified-purchase check for reviews). */
    @Query("""
            select o.id from Order o join o.items i
            where o.user.id = :userId and i.productId = :productId
              and o.status = com.shopsmart.order.OrderStatus.DELIVERED
            order by o.deliveredAt desc
            """)
    List<Long> findDeliveredOrderIdsContaining(@Param("userId") Long userId, @Param("productId") Long productId);

    /** Product IDs from valid placed orders, used to avoid recommending repeat purchases. */
    @Query("""
            select distinct i.productId from Order o join o.items i
            where o.user.id = :userId
              and o.status not in (com.shopsmart.order.OrderStatus.PENDING_PAYMENT,
                                   com.shopsmart.order.OrderStatus.CANCELLED)
            """)
    List<Long> findPurchasedProductIds(@Param("userId") Long userId);

    long countByStatus(OrderStatus status);
}

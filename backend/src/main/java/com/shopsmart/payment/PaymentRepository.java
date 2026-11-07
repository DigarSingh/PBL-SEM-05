package com.shopsmart.payment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p where p.order.id = :orderId")
    Optional<Payment> findByOrderId(@Param("orderId") Long orderId);

    @Query("select p from Payment p where p.order.id in :orderIds")
    List<Payment> findByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order o where p.id = :id")
    Optional<Payment> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order o where p.razorpayOrderId = :razorpayOrderId")
    Optional<Payment> lockByRazorpayOrderId(@Param("razorpayOrderId") String razorpayOrderId);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    @Query(value = """
            select p from Payment p join fetch p.order o join fetch o.user u
            where (:status is null or p.status = :status)
              and (:method is null or p.method = :method)
              and (:orderStatus is null or o.status = :orderStatus)
              and (:includePending = true or o.status <> com.shopsmart.order.OrderStatus.PENDING_PAYMENT
                   or :orderStatus is not null)
              and (:q is null or lower(o.orderNumber) like :q or lower(u.email) like :q or lower(u.fullName) like :q)
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            """,
            countQuery = """
            select count(p) from Payment p join p.order o join o.user u
            where (:status is null or p.status = :status)
              and (:method is null or p.method = :method)
              and (:orderStatus is null or o.status = :orderStatus)
              and (:includePending = true or o.status <> com.shopsmart.order.OrderStatus.PENDING_PAYMENT
                   or :orderStatus is not null)
              and (:q is null or lower(o.orderNumber) like :q or lower(u.email) like :q or lower(u.fullName) like :q)
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            """)
    Page<Payment> searchOrders(@Param("status") PaymentStatus status, @Param("method") PaymentMethod method,
                               @Param("orderStatus") com.shopsmart.order.OrderStatus orderStatus,
                               @Param("includePending") boolean includePending, @Param("q") String likePattern,
                               @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select p from Payment p join fetch p.order o
            where p.method = com.shopsmart.payment.PaymentMethod.RAZORPAY
              and p.status = com.shopsmart.payment.PaymentStatus.AUTHORIZED and p.updatedAt < :before
            """)
    List<Payment> findAuthorizedBefore(@Param("before") Instant before, Pageable pageable);

    @Query("""
            select p from Payment p join fetch p.order o
            where p.method = com.shopsmart.payment.PaymentMethod.RAZORPAY
              and p.status = com.shopsmart.payment.PaymentStatus.PAID
              and o.status = com.shopsmart.order.OrderStatus.CANCELLED
            """)
    List<Payment> findRefundPending(Pageable pageable);
}

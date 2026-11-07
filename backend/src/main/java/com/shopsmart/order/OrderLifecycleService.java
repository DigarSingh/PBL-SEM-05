package com.shopsmart.order;

import com.shopsmart.activity.ActivityService;
import com.shopsmart.activity.ActivityType;
import com.shopsmart.admin.AuditService;
import com.shopsmart.cart.CartService;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.BusinessTime;
import com.shopsmart.coupon.CouponService;
import com.shopsmart.inventory.Inventory;
import com.shopsmart.inventory.InventoryService;
import com.shopsmart.order.events.OrderCancelledEvent;
import com.shopsmart.order.events.OrderPlacedEvent;
import com.shopsmart.order.events.OrderStatusChangedEvent;
import com.shopsmart.payment.Payment;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentRepository;
import com.shopsmart.payment.PaymentStatus;
import com.shopsmart.product.ProductRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns order state changes and their side effects (stock, coupons, sold counts, history,
 * events). All methods run inside the caller's transaction.
 */
@Service
public class OrderLifecycleService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final CartService cartService;
    private final ProductRepository productRepository;
    private final ActivityService activityService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrderLifecycleService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                 InventoryService inventoryService, CouponService couponService,
                                 CartService cartService, ProductRepository productRepository,
                                 ActivityService activityService, AuditService auditService,
                                 ApplicationEventPublisher events, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryService = inventoryService;
        this.couponService = couponService;
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.activityService = activityService;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    /** Completes a successfully paid online order: reservation becomes a sale and the order is placed. */
    @Transactional
    public void placePaidOnlineOrder(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        Map<Long, Integer> quantities = quantitiesByVariant(order);
        Map<Long, Inventory> locked = inventoryService.lockForVariants(quantities.keySet());
        quantities.forEach((variantId, units) -> {
            Inventory inventory = locked.get(variantId);
            if (inventory != null) {
                inventoryService.commitReservation(inventory, units, order.getId());
            }
        });
        transition(order, OrderStatus.PLACED, null, "Payment received");
        markPlaced(order);
        recordSale(order);
        cartService.removePurchased(order.getUser().getId(), quantities);
        events.publishEvent(new OrderPlacedEvent(order.getId()));
    }

    /** Side effects shared by every placed order (COD immediately, online after payment). */
    public void markPlaced(Order order) {
        Instant now = clock.instant();
        order.setPlacedAt(now);
        order.setEstimatedDeliveryDate(estimateDelivery(now));
    }

    public void recordSale(Order order) {
        for (OrderItem item : order.getItems()) {
            productRepository.incrementSoldCount(item.getProductId(), item.getQuantity());
            activityService.record(order.getUser().getId(), ActivityType.PURCHASE, item.getProductId(), null,
                    Map.of("orderId", order.getId(), "quantity", item.getQuantity()));
        }
    }

    /** Administrator status change along the fulfilment pipeline. */
    @Transactional
    public Order updateStatus(Long orderId, OrderStatus next, String note, Long adminId) {
        Order order = orderRepository.findWithUser(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (next == OrderStatus.CANCELLED) {
            return cancel(order, StringUtils.hasText(note) ? note : "Cancelled by the store", adminId, false);
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw invalidTransition(order.getStatus(), next, "The order has not been paid yet");
        }
        if (!order.getStatus().canTransitionTo(next)) {
            throw invalidTransition(order.getStatus(), next, null);
        }
        OrderStatus previous = order.getStatus();
        transition(order, next, adminId, note);
        if (next == OrderStatus.DELIVERED) {
            order.setDeliveredAt(clock.instant());
        }
        auditService.record("ORDER_STATUS_CHANGED", "ORDER", orderId,
                Map.of("from", previous.name(), "to", next.name(), "note", note == null ? "" : note));
        events.publishEvent(new OrderStatusChangedEvent(orderId, previous, next, note));
        return order;
    }

    /**
     * Cancels an order, returning stock (or releasing the reservation) and freeing its coupon.
     * Captured online payments are refunded after commit by the payment module.
     */
    @Transactional
    public Order cancel(Order order, String reason, Long actorId, boolean byCustomer) {
        OrderStatus previous = order.getStatus();
        boolean allowed = byCustomer ? previous.isCancellableByCustomer() : previous.canTransitionTo(OrderStatus.CANCELLED);
        if (!allowed) {
            throw invalidTransition(previous, OrderStatus.CANCELLED, byCustomer
                    ? "Orders can only be cancelled before they are processed. Please contact support."
                    : "Orders cannot be cancelled once shipped");
        }
        Map<Long, Integer> quantities = quantitiesByVariant(order);
        Map<Long, Inventory> locked = inventoryService.lockForVariants(quantities.keySet());
        quantities.forEach((variantId, units) -> {
            Inventory inventory = locked.get(variantId);
            if (inventory == null) {
                return;
            }
            if (previous == OrderStatus.PENDING_PAYMENT) {
                inventoryService.releaseReservation(inventory, units, order.getId());
            } else {
                inventoryService.restockCancelled(inventory, units, order.getId());
            }
        });
        if (previous.holdsCommittedStock()) {
            order.getItems().forEach(item -> productRepository.incrementSoldCount(item.getProductId(),
                    -item.getQuantity()));
        }
        couponService.releaseForOrder(order.getId());

        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment != null && !payment.getStatus().isSettled() && payment.getStatus() != PaymentStatus.REFUNDED) {
            payment.setStatus(payment.getMethod() == PaymentMethod.RAZORPAY && payment.getStatus() == PaymentStatus.FAILED
                    ? PaymentStatus.FAILED : PaymentStatus.CANCELLED);
        }
        String cleanReason = StringUtils.hasText(reason) ? reason.trim() : "Cancelled";
        order.setCancelledAt(clock.instant());
        order.setCancellationReason(cleanReason);
        transition(order, OrderStatus.CANCELLED, actorId, cleanReason);
        if (!byCustomer && actorId != null) {
            auditService.record("ORDER_CANCELLED", "ORDER", order.getId(),
                    Map.of("from", previous.name(), "reason", cleanReason));
        }
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), previous, OrderStatus.CANCELLED, cleanReason));
        events.publishEvent(new OrderCancelledEvent(order.getId(), payment == null ? null : payment.getId(), cleanReason));
        return order;
    }

    @Transactional
    public Order cancelByCustomer(Long userId, Long orderId, String reason) {
        Order order = orderRepository.findWithUser(orderId)
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order"));
        return cancel(order, StringUtils.hasText(reason) ? reason : "Cancelled by customer", userId, true);
    }

    void transition(Order order, OrderStatus next, Long actorId, String note) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setFromStatus(order.getStatus());
        entry.setToStatus(next);
        entry.setChangedByUserId(actorId);
        entry.setNote(StringUtils.hasText(note) ? note.trim() : null);
        order.setStatus(next);
        order.addHistory(entry);
    }

    static Map<Long, Integer> quantitiesByVariant(Order order) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        order.getItems().forEach(item -> quantities.merge(item.getVariantId(), item.getQuantity(), Integer::sum));
        return quantities;
    }

    /** Four business days, skipping Sundays. */
    private static LocalDate estimateDelivery(Instant placedAt) {
        LocalDate date = placedAt.atZone(BusinessTime.ZONE).toLocalDate();
        int added = 0;
        while (added < 4) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }

    private static ApiException invalidTransition(OrderStatus from, OrderStatus to, String reason) {
        String message = reason != null ? reason
                : "Cannot change an order from " + OrderMapper.label(from) + " to " + OrderMapper.label(to);
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_ORDER_TRANSITION, message,
                Map.of("from", from.name(), "to", to.name(), "allowed", from.allowedTransitions()));
    }
}

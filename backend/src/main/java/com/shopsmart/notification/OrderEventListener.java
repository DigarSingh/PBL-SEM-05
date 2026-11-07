package com.shopsmart.notification;

import com.shopsmart.common.util.Money;
import com.shopsmart.coupon.CouponRules;
import com.shopsmart.inventory.events.LowStockEvent;
import com.shopsmart.notification.email.EmailService;
import com.shopsmart.notification.email.EmailTemplates;
import com.shopsmart.order.Order;
import com.shopsmart.order.OrderItem;
import com.shopsmart.order.OrderMapper;
import com.shopsmart.order.OrderRepository;
import com.shopsmart.order.OrderStatus;
import com.shopsmart.order.events.OrderPlacedEvent;
import com.shopsmart.order.events.OrderStatusChangedEvent;
import com.shopsmart.payment.Payment;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentRepository;
import com.shopsmart.payment.events.PaymentFailedEvent;
import com.shopsmart.payment.events.PaymentRefundedEvent;
import com.shopsmart.payment.events.PaymentSucceededEvent;
import com.shopsmart.realtime.InventoryAlertMessage;
import com.shopsmart.realtime.OrderUpdateMessage;
import com.shopsmart.realtime.RealtimePublisher;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns committed order/payment/stock events into notifications, WebSocket pushes and e-mails.
 * Runs after commit so nothing is announced for work that rolled back; failures here are logged
 * and never affect the (already committed) business operation.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private static final Map<OrderStatus, String> CUSTOMER_MESSAGES = Map.of(
            OrderStatus.CONFIRMED, "has been confirmed and will be packed soon.",
            OrderStatus.PROCESSING, "is being packed.",
            OrderStatus.SHIPPED, "has been shipped.",
            OrderStatus.OUT_FOR_DELIVERY, "is out for delivery. Please keep your phone handy.",
            OrderStatus.DELIVERED, "has been delivered. Enjoy your purchase!",
            OrderStatus.CANCELLED, "has been cancelled.");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;
    private final EmailService emailService;
    private final EmailTemplates templates;
    private final TransactionTemplate tx;
    private final TransactionTemplate requiresNew;
    private final Executor executor;
    private final Clock clock;

    public OrderEventListener(OrderRepository orderRepository, PaymentRepository paymentRepository,
                              NotificationService notifications, RealtimePublisher realtime,
                              EmailService emailService, EmailTemplates templates, TransactionTemplate tx,
                              PlatformTransactionManager transactionManager,
                              @Qualifier("applicationTaskExecutor") Executor executor, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.notifications = notifications;
        this.realtime = realtime;
        this.emailService = emailService;
        this.templates = templates;
        this.tx = tx;
        // After commit the original transaction is finished: new writes need their own transaction.
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderPlaced(OrderPlacedEvent event) {
        safely("order placed", () -> {
            Snapshot order = snapshot(event.orderId());
            notifications.notifyUser(order.userId(), NotificationType.ORDER_PLACED, "Order placed",
                    "Your order " + order.number() + " for " + order.total() + " has been placed.", order.link());
            notifications.notifyAdmins(NotificationType.NEW_ORDER, "New order " + order.number(),
                    order.customerName() + " placed an order of " + order.total() + " ("
                            + (order.method() == PaymentMethod.COD ? "Cash on Delivery" : "paid online") + ").",
                    "/admin/orders/" + order.id());
            push(order, "ORDER_PLACED", "Order " + order.number() + " placed");
            email(order, "Order " + order.number() + " confirmed", "Thank you for your order!", withItems(order,
                    "We have received your order " + order.number() + " and will notify you as it progresses."));
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        safely("payment succeeded", () -> {
            Snapshot order = snapshot(event.orderId());
            notifications.notifyUser(order.userId(), NotificationType.PAYMENT_SUCCEEDED, "Payment successful",
                    "We received " + order.total() + " for order " + order.number() + ".", order.link());
            email(order, "Payment received for order " + order.number(), "Payment successful",
                    List.of("We have received your payment of " + order.total() + " for order " + order.number() + ".",
                            "Payment reference: " + (order.paymentReference() == null ? "-" : order.paymentReference())));
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentFailed(PaymentFailedEvent event) {
        safely("payment failed", () -> {
            Snapshot order = snapshot(event.orderId());
            notifications.notifyUser(order.userId(), NotificationType.PAYMENT_FAILED, "Payment failed",
                    "The payment for order " + order.number() + " did not go through. You can retry from the order page.",
                    order.link());
            push(order, "PAYMENT_FAILED", "Payment failed for order " + order.number());
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        safely("payment refunded", () -> {
            Snapshot order = snapshot(event.orderId());
            notifications.notifyUser(order.userId(), NotificationType.PAYMENT_REFUNDED, "Refund initiated",
                    "A refund of " + order.total() + " for order " + order.number()
                            + " has been initiated. It usually reaches your account in 5-7 business days.",
                    order.link());
            push(order, "PAYMENT_REFUNDED", "Refund initiated for order " + order.number());
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        safely("status changed", () -> {
            Snapshot order = snapshot(event.orderId());
            String text = "Your order " + order.number() + " " + CUSTOMER_MESSAGES.getOrDefault(event.to(), "was updated.");
            NotificationType type = event.to() == OrderStatus.CANCELLED
                    ? NotificationType.ORDER_CANCELLED : NotificationType.ORDER_STATUS_CHANGED;
            notifications.notifyUser(order.userId(), type, OrderMapper.label(event.to()), text, order.link());
            push(order, "ORDER_STATUS_CHANGED", text);
            switch (event.to()) {
                case SHIPPED, OUT_FOR_DELIVERY -> email(order, "Order " + order.number() + ": "
                        + OrderMapper.label(event.to()), "Shipping update", List.of(text));
                case DELIVERED -> email(order, "Order " + order.number() + " delivered", "Delivered",
                        List.of(text, "We would love to hear what you think - you can now review your items."));
                case CANCELLED -> email(order, "Order " + order.number() + " cancelled", "Order cancelled",
                        List.of(text, "Reason: " + (event.note() == null ? "-" : event.note()),
                                order.method() == PaymentMethod.RAZORPAY
                                        ? "If you were charged, the amount will be refunded to your original payment method."
                                        : "No payment was collected for this order."));
                default -> {
                    // Confirmed / processing: notification and live update only.
                }
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLowStock(LowStockEvent event) {
        safely("low stock", () -> {
            notifications.notifyAdmins(NotificationType.LOW_STOCK, "Low stock: " + event.productName(),
                    event.sku() + " has " + event.available() + " unit(s) left (threshold " + event.threshold() + ").",
                    "/admin/inventory?lowStock=true");
            realtime.inventoryAlert(new InventoryAlertMessage(event.variantId(), event.productId(), event.productName(),
                    event.sku(), event.available(), event.threshold(), clock.instant()));
        });
    }

    private void push(Snapshot order, String type, String message) {
        realtime.orderUpdated(order.userId(), new OrderUpdateMessage(type, order.id(), order.number(),
                order.status().name(), OrderMapper.label(order.status()),
                order.paymentStatus() == null ? null : order.paymentStatus(), message, clock.instant()));
    }

    private void email(Snapshot order, String subject, String heading, List<String> paragraphs) {
        executor.execute(() -> emailService.send(templates.orderEmail(order.email(), subject, heading, paragraphs,
                String.valueOf(order.id()))));
    }

    private static List<String> withItems(Snapshot order, String intro) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(intro);
        order.items().forEach(line -> paragraphs.add("• " + line));
        paragraphs.add("Order total: " + order.total());
        return paragraphs;
    }

    private Snapshot snapshot(Long orderId) {
        return tx.execute(status -> {
            Order order = orderRepository.findWithUser(orderId).orElseThrow();
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            List<String> items = order.getItems().stream()
                    .map((OrderItem item) -> item.getQuantity() + " x " + item.getProductName()
                            + (item.getVariantName().equals("Standard") ? "" : " (" + item.getVariantName() + ")"))
                    .toList();
            return new Snapshot(order.getId(), order.getOrderNumber(), order.getUser().getId(),
                    order.getUser().getEmail(), order.getUser().getFullName(), order.getStatus(),
                    order.getPaymentMethod(), payment == null ? null : payment.getStatus().name(),
                    payment == null ? null : payment.getRazorpayPaymentId(),
                    CouponRules.format(Money.of(order.getTotalAmount())), items);
        });
    }

    private void safely(String what, Runnable action) {
        try {
            requiresNew.executeWithoutResult(status -> action.run());
        } catch (RuntimeException e) {
            log.error("Post-commit handling of '{}' failed", what, e);
        }
    }

    private record Snapshot(Long id, String number, Long userId, String email, String customerName,
                            OrderStatus status, PaymentMethod method, String paymentStatus, String paymentReference,
                            String total, List<String> items) {

        String link() {
            return "/orders/" + id;
        }
    }
}

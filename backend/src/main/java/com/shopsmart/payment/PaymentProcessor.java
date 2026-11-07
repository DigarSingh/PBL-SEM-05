package com.shopsmart.payment;

import com.shopsmart.order.Order;
import com.shopsmart.order.OrderLifecycleService;
import com.shopsmart.order.OrderStatus;
import com.shopsmart.payment.events.PaymentFailedEvent;
import com.shopsmart.payment.events.PaymentSucceededEvent;
import com.shopsmart.payment.events.RefundRequiredEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Applies a gateway-confirmed payment outcome to a payment row (locked) and its order. Shared by
 * checkout verification, webhooks and reconciliation, and idempotent: applying the same outcome
 * twice is a no-op, and a failure never overrides a success.
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentRepository paymentRepository;
    private final OrderLifecycleService lifecycle;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PaymentProcessor(PaymentRepository paymentRepository, OrderLifecycleService lifecycle,
                            ApplicationEventPublisher events, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.lifecycle = lifecycle;
        this.events = events;
        this.clock = clock;
    }

    public enum Outcome { PLACED, UPGRADED, ALREADY_APPLIED, REFUND_REQUIRED, FAILED, IGNORED }

    /** {@code status} must be PAID, AUTHORIZED or FAILED. */
    @Transactional
    public Outcome apply(Payment lockedPayment, PaymentStatus status, String razorpayPaymentId,
                         String providerMethod, String failureReason) {
        Payment payment = lockedPayment;
        Order order = payment.getOrder();
        if (status == PaymentStatus.FAILED) {
            if (payment.getStatus().isSettled() || payment.getStatus() == PaymentStatus.REFUNDED) {
                return Outcome.IGNORED;
            }
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailedAt(clock.instant());
            payment.setFailureReason(truncate(StringUtils.hasText(failureReason) ? failureReason : "Payment failed"));
            if (StringUtils.hasText(razorpayPaymentId)) {
                payment.setRazorpayPaymentId(razorpayPaymentId);
            }
            events.publishEvent(new PaymentFailedEvent(order.getId(), payment.getFailureReason()));
            return Outcome.FAILED;
        }

        if (payment.getStatus() == PaymentStatus.PAID
                || payment.getStatus() == PaymentStatus.REFUNDED
                || (payment.getStatus() == PaymentStatus.AUTHORIZED && status == PaymentStatus.AUTHORIZED)) {
            return Outcome.ALREADY_APPLIED;
        }
        boolean wasAuthorized = payment.getStatus() == PaymentStatus.AUTHORIZED;
        payment.setStatus(status);
        payment.setFailureReason(null);
        if (StringUtils.hasText(razorpayPaymentId)) {
            payment.setRazorpayPaymentId(razorpayPaymentId);
        }
        if (StringUtils.hasText(providerMethod)) {
            payment.setProviderMethod(providerMethod);
        }
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(clock.instant());
        }
        if (wasAuthorized) {
            return Outcome.UPGRADED;
        }
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            lifecycle.placePaidOnlineOrder(order);
            events.publishEvent(new PaymentSucceededEvent(order.getId(), payment.getId()));
            return Outcome.PLACED;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Payment {} captured for cancelled order {}; refund required", payment.getId(), order.getId());
            events.publishEvent(new RefundRequiredEvent(payment.getId()));
            return Outcome.REFUND_REQUIRED;
        }
        return Outcome.ALREADY_APPLIED;
    }

    private static String truncate(String value) {
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}

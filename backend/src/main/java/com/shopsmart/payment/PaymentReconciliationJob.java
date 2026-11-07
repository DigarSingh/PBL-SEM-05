package com.shopsmart.payment;

import com.shopsmart.admin.StoreSettingsService;
import com.shopsmart.order.Order;
import com.shopsmart.order.OrderLifecycleService;
import com.shopsmart.order.OrderRepository;
import com.shopsmart.order.OrderStatus;
import com.shopsmart.payment.razorpay.PaymentGatewayException;
import com.shopsmart.payment.razorpay.RazorpayGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Safety net for lost callbacks and webhooks: settles or expires unpaid online orders (checking
 * Razorpay first, so a paid order is never cancelled), captures lingering authorisations and
 * retries failed refunds. Every step is idempotent, so running it on several instances is safe.
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private static final Duration GIVE_UP_ON_GATEWAY_AFTER = Duration.ofHours(24);
    private static final int BATCH = 50;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProcessor processor;
    private final PaymentService paymentService;
    private final OrderLifecycleService lifecycle;
    private final RazorpayGateway gateway;
    private final StoreSettingsService settingsService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentReconciliationJob(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                    PaymentProcessor processor, PaymentService paymentService,
                                    OrderLifecycleService lifecycle, RazorpayGateway gateway,
                                    StoreSettingsService settingsService, TransactionTemplate tx, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.processor = processor;
        this.paymentService = paymentService;
        this.lifecycle = lifecycle;
        this.gateway = gateway;
        this.settingsService = settingsService;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.jobs.pending-payment-check-interval}", initialDelayString = "PT1M")
    public void run() {
        try {
            expireOrSettlePendingOrders();
            captureAuthorizedPayments();
            retryPendingRefunds();
        } catch (RuntimeException e) {
            log.error("Payment reconciliation run failed", e);
        }
    }

    public void expireOrSettlePendingOrders() {
        Instant now = clock.instant();
        Duration timeout = Duration.ofMinutes(settingsService.current().getPendingPaymentTimeoutMinutes());
        List<Order> overdue = tx.execute(status -> orderRepository.findPendingPaymentCreatedBefore(now.minus(timeout),
                PageRequest.of(0, BATCH)));
        for (Order order : overdue) {
            try {
                settleOrExpire(order.getId(), now);
            } catch (RuntimeException e) {
                log.warn("Could not reconcile pending order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    private void settleOrExpire(Long orderId, Instant now) {
        Payment snapshot = tx.execute(status -> paymentRepository.findByOrderId(orderId).orElseThrow());
        Order order = tx.execute(status -> orderRepository.findById(orderId).orElseThrow());
        if (snapshot.getRazorpayOrderId() != null) {
            try {
                List<RazorpayGateway.RazorpayPayment> attempts = gateway.fetchOrderPayments(snapshot.getRazorpayOrderId());
                RazorpayGateway.RazorpayPayment settled = attempts.stream()
                        .filter(attempt -> attempt.isCaptured() || attempt.isAuthorized())
                        .findFirst().orElse(null);
                if (settled != null) {
                    tx.executeWithoutResult(status -> processor.apply(
                            paymentRepository.lockById(snapshot.getId()).orElseThrow(),
                            settled.isCaptured() ? PaymentStatus.PAID : PaymentStatus.AUTHORIZED, settled.id(),
                            settled.method(), null));
                    log.info("Settled pending order {} from Razorpay state", orderId);
                    return;
                }
            } catch (PaymentGatewayException e) {
                if (order.getCreatedAt().isAfter(now.minus(GIVE_UP_ON_GATEWAY_AFTER))) {
                    log.warn("Razorpay unavailable; will retry pending order {} later", orderId);
                    return;
                }
            }
        }
        tx.executeWithoutResult(status -> orderRepository.findWithUser(orderId)
                .filter(candidate -> candidate.getStatus() == OrderStatus.PENDING_PAYMENT)
                .ifPresent(candidate -> lifecycle.cancel(candidate, "Payment was not completed in time", null, false)));
        log.info("Expired unpaid order {}", orderId);
    }

    public void captureAuthorizedPayments() {
        List<Payment> authorized = tx.execute(status -> paymentRepository.findAuthorizedBefore(
                clock.instant().minus(Duration.ofMinutes(2)), PageRequest.of(0, BATCH)));
        for (Payment payment : authorized) {
            if (payment.getOrder().getStatus() == OrderStatus.CANCELLED) {
                paymentService.refundQuietly(payment.getId());
                continue;
            }
            try {
                RazorpayGateway.RazorpayPayment captured = gateway.capture(payment.getRazorpayPaymentId(),
                        com.shopsmart.common.util.Money.toMinorUnits(payment.getAmount()), payment.getCurrency());
                if (captured.isCaptured()) {
                    tx.executeWithoutResult(status -> processor.apply(
                            paymentRepository.lockById(payment.getId()).orElseThrow(), PaymentStatus.PAID,
                            captured.id(), captured.method(), null));
                }
            } catch (PaymentGatewayException e) {
                log.warn("Capture retry failed for payment {}: {}", payment.getId(), e.getMessage());
            }
        }
    }

    public void retryPendingRefunds() {
        List<Payment> pending = tx.execute(status -> paymentRepository.findRefundPending(PageRequest.of(0, BATCH)));
        pending.forEach(payment -> paymentService.refundQuietly(payment.getId()));
    }
}

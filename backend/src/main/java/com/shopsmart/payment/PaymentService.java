package com.shopsmart.payment;

import com.shopsmart.admin.AuditService;
import com.shopsmart.admin.StoreSettingsService;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.Money;
import com.shopsmart.config.AppProperties;
import com.shopsmart.order.Order;
import com.shopsmart.order.OrderLifecycleService;
import com.shopsmart.order.OrderRepository;
import com.shopsmart.order.OrderStatus;
import com.shopsmart.order.dto.RazorpayCheckoutResponse;
import com.shopsmart.order.events.OrderCancelledEvent;
import com.shopsmart.payment.dto.AdminPaymentResponse;
import com.shopsmart.payment.dto.RazorpayFailureRequest;
import com.shopsmart.payment.dto.RazorpayVerifyRequest;
import com.shopsmart.payment.events.PaymentRefundedEvent;
import com.shopsmart.payment.events.RefundRequiredEvent;
import com.shopsmart.payment.razorpay.PaymentGatewayException;
import com.shopsmart.payment.razorpay.RazorpayGateway;
import com.shopsmart.payment.razorpay.RazorpaySignatures;
import com.shopsmart.user.User;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Razorpay payment flows. Network calls to the gateway are always made outside database
 * transactions, so no row locks are held while waiting on the network.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProcessor processor;
    private final OrderLifecycleService lifecycle;
    private final RazorpayGateway gateway;
    private final RazorpaySignatures signatures;
    private final StoreSettingsService settingsService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final AppProperties properties;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                          PaymentProcessor processor, OrderLifecycleService lifecycle, RazorpayGateway gateway,
                          RazorpaySignatures signatures, StoreSettingsService settingsService,
                          AuditService auditService, ApplicationEventPublisher events, TransactionTemplate tx,
                          AppProperties properties, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.processor = processor;
        this.lifecycle = lifecycle;
        this.gateway = gateway;
        this.signatures = signatures;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.events = events;
        this.tx = tx;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates (once) the Razorpay order for a pending online order and returns the parameters for
     * Razorpay Checkout. Safe to call repeatedly, e.g. to retry a failed payment.
     */
    public RazorpayCheckoutResponse startRazorpayPayment(Long orderId, Long userId) {
        PendingPayment pending = tx.execute(status -> {
            Order order = orderRepository.findWithUser(orderId)
                    .filter(candidate -> candidate.getUser().getId().equals(userId))
                    .orElseThrow(() -> ApiException.notFound("Order"));
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentMethod() != PaymentMethod.RAZORPAY) {
                throw ApiException.businessRule("This order does not need an online payment");
            }
            Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
            User user = order.getUser();
            return new PendingPayment(payment.getId(), payment.getRazorpayOrderId(), order.getId(),
                    order.getOrderNumber(), Money.toMinorUnits(order.getTotalAmount()), order.getCurrency(),
                    user.getFullName(), user.getEmail(),
                    razorpayContact(order.getShippingAddress().getPhone(), user.getPhone()));
        });
        if (pending.amountPaise() < 100) {
            throw ApiException.businessRule("Online payments require a minimum order amount of INR 1.00");
        }
        String razorpayOrderId = pending.razorpayOrderId();
        if (razorpayOrderId == null) {
            RazorpayGateway.RazorpayOrder created = gateway.createOrder(pending.amountPaise(), pending.currency(),
                    pending.orderNumber(), Map.of("shopsmart_order_id", String.valueOf(pending.orderId()),
                            "shopsmart_order_number", pending.orderNumber()));
            razorpayOrderId = tx.execute(status -> {
                Payment payment = paymentRepository.lockById(pending.paymentId()).orElseThrow();
                if (payment.getRazorpayOrderId() == null) {
                    payment.setRazorpayOrderId(created.id());
                }
                return payment.getRazorpayOrderId();
            });
        }
        return new RazorpayCheckoutResponse(properties.razorpay().keyId(), razorpayOrderId, pending.amountPaise(),
                pending.currency(), settingsService.current().getStoreName(), "Order " + pending.orderNumber(),
                pending.orderId(), pending.orderNumber(),
                new RazorpayCheckoutResponse.Prefill(pending.name(), pending.email(), pending.phone()));
    }

    /**
     * Verifies the checkout signature, then confirms the payment state with Razorpay before
     * marking anything as paid. Client-reported success is never trusted on its own.
     */
    public void verifyRazorpayPayment(Long userId, RazorpayVerifyRequest request) {
        Payment snapshot = tx.execute(status -> paymentRepository.findByOrderId(request.orderId())
                .filter(payment -> payment.getOrder().getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order")));
        if (snapshot.getMethod() != PaymentMethod.RAZORPAY
                || !request.razorpayOrderId().equals(snapshot.getRazorpayOrderId())) {
            throw verificationFailed("The payment does not belong to this order");
        }
        if (!signatures.isValidPaymentSignature(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            log.warn("Invalid Razorpay signature for order {} (payment {})", request.orderId(),
                    request.razorpayPaymentId());
            throw verificationFailed("Payment verification failed. If money was deducted it will be refunded.");
        }
        if (snapshot.getStatus() == PaymentStatus.PAID || snapshot.getStatus() == PaymentStatus.AUTHORIZED) {
            return;
        }

        PaymentStatus confirmed = PaymentStatus.AUTHORIZED;
        String providerMethod = null;
        try {
            RazorpayGateway.RazorpayPayment gatewayPayment = gateway.fetchPayment(request.razorpayPaymentId());
            if (!request.razorpayOrderId().equals(gatewayPayment.orderId())
                    || gatewayPayment.amount() != Money.toMinorUnits(snapshot.getAmount())) {
                throw verificationFailed("The payment details do not match this order");
            }
            providerMethod = gatewayPayment.method();
            if (gatewayPayment.isCaptured()) {
                confirmed = PaymentStatus.PAID;
            } else if (gatewayPayment.isAuthorized()) {
                confirmed = captureQuietly(gatewayPayment.id(), gatewayPayment.amount(), gatewayPayment.currency());
            } else if (gatewayPayment.isFailed()) {
                recordFailure(snapshot.getId(), request.razorpayPaymentId(), gatewayPayment.errorDescription());
                throw verificationFailed("The payment failed: " + (gatewayPayment.errorDescription() == null
                        ? "please try again" : gatewayPayment.errorDescription()));
            }
        } catch (PaymentGatewayException e) {
            // The signature proves Razorpay authorised this payment; webhooks / reconciliation settle capture.
            log.warn("Could not confirm payment {} with Razorpay ({}); treating as authorised",
                    request.razorpayPaymentId(), e.getMessage());
        }
        PaymentStatus finalStatus = confirmed;
        String finalMethod = providerMethod;
        tx.executeWithoutResult(status -> processor.apply(paymentRepository.lockById(snapshot.getId()).orElseThrow(),
                finalStatus, request.razorpayPaymentId(), finalMethod, null));
    }

    /** Records a failed attempt reported by Razorpay Checkout; the order stays payable until it expires. */
    public void recordCheckoutFailure(Long userId, RazorpayFailureRequest request) {
        Payment snapshot = tx.execute(status -> paymentRepository.findByOrderId(request.orderId())
                .filter(payment -> payment.getOrder().getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order")));
        if (snapshot.getMethod() != PaymentMethod.RAZORPAY) {
            throw ApiException.badRequest("This order is not paid online");
        }
        String reason = StringUtils.hasText(request.description()) ? request.description() : "Payment was not completed";
        recordFailure(snapshot.getId(), request.razorpayPaymentId(), reason);
    }

    private void recordFailure(Long paymentId, String razorpayPaymentId, String reason) {
        tx.executeWithoutResult(status -> processor.apply(paymentRepository.lockById(paymentId).orElseThrow(),
                PaymentStatus.FAILED, razorpayPaymentId, null, reason));
    }

    private PaymentStatus captureQuietly(String paymentId, long amount, String currency) {
        try {
            return gateway.capture(paymentId, amount, currency).isCaptured() ? PaymentStatus.PAID : PaymentStatus.AUTHORIZED;
        } catch (PaymentGatewayException e) {
            log.warn("Capture of payment {} failed ({}); it will be retried", paymentId, e.getMessage());
            return PaymentStatus.AUTHORIZED;
        }
    }

    /** After a cancellation commits, refund captured online payments (asynchronously). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (event.paymentId() != null) {
            refundQuietly(event.paymentId());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefundRequired(RefundRequiredEvent event) {
        refundQuietly(event.paymentId());
    }

    /** Admin action: retry a refund that failed earlier. */
    public AdminPaymentResponse retryRefund(Long paymentId) {
        refund(paymentId);
        return tx.execute(status -> toAdminResponse(paymentRepository.findById(paymentId).orElseThrow()));
    }

    void refundQuietly(Long paymentId) {
        try {
            refund(paymentId);
        } catch (ApiException e) {
            log.error("Refund of payment {} failed: {}. It will be retried by reconciliation.", paymentId,
                    e.getMessage());
        }
    }

    /** Refunds a captured payment of a cancelled order; authorisations that were never captured just lapse. */
    void refund(Long paymentId) {
        Payment snapshot = tx.execute(status -> paymentRepository.findById(paymentId).orElseThrow());
        Order order = tx.execute(status -> orderRepository.findById(snapshot.getOrder().getId()).orElseThrow());
        if (snapshot.getMethod() != PaymentMethod.RAZORPAY || order.getStatus() != OrderStatus.CANCELLED) {
            return;
        }
        if (snapshot.getStatus() == PaymentStatus.AUTHORIZED) {
            tx.executeWithoutResult(status -> {
                Payment payment = paymentRepository.lockById(paymentId).orElseThrow();
                if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    payment.setFailureReason("Authorisation released: the order was cancelled before capture");
                }
            });
            return;
        }
        if (snapshot.getStatus() != PaymentStatus.PAID) {
            return;
        }
        RazorpayGateway.RazorpayRefund refund = gateway.refund(snapshot.getRazorpayPaymentId(),
                Money.toMinorUnits(snapshot.getAmount()),
                Map.of("shopsmart_order_number", order.getOrderNumber(), "reason", "Order cancelled"));
        tx.executeWithoutResult(status -> {
            Payment payment = paymentRepository.lockById(paymentId).orElseThrow();
            if (payment.getStatus() == PaymentStatus.PAID) {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundId(refund.id());
                payment.setRefundedAmount(Money.fromMinorUnits(refund.amount()));
                payment.setRefundedAt(clock.instant());
                events.publishEvent(new PaymentRefundedEvent(order.getId(), paymentId));
            }
        });
        log.info("Refunded payment {} for cancelled order {} (refund {})", paymentId, order.getOrderNumber(),
                refund.id());
    }

    /** Admin action: cash was collected for a delivered (or out-for-delivery) COD order. */
    @Transactional
    public AdminPaymentResponse collectCod(Long paymentId, Long adminId) {
        Payment payment = paymentRepository.lockById(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (payment.getMethod() != PaymentMethod.COD) {
            throw ApiException.businessRule("Only Cash on Delivery payments can be marked as collected");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return toAdminResponse(payment);
        }
        OrderStatus orderStatus = payment.getOrder().getStatus();
        if (orderStatus != OrderStatus.OUT_FOR_DELIVERY && orderStatus != OrderStatus.DELIVERED) {
            throw ApiException.businessRule("Cash can only be collected once the order is out for delivery");
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(clock.instant());
        payment.setCollectedByUserId(adminId);
        auditService.record("COD_COLLECTED", "PAYMENT", paymentId,
                Map.of("orderNumber", payment.getOrder().getOrderNumber(), "amount", payment.getAmount()));
        return toAdminResponse(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> search(PaymentStatus status, PaymentMethod method, String q,
                                                     int page, int size) {
        String pattern = StringUtils.hasText(q) ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : null;
        return PageResponse.from(paymentRepository.searchOrders(status, method, null, true, pattern, null, null,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), this::toAdminResponse);
    }

    /** Called when the gateway could not create a payment order: undo the pending order. */
    public void cancelAfterGatewayFailure(Long orderId) {
        tx.executeWithoutResult(status -> orderRepository.findWithUser(orderId)
                .filter(order -> order.getStatus() == OrderStatus.PENDING_PAYMENT)
                .ifPresent(order -> lifecycle.cancel(order, "Payment could not be started", null, false)));
    }

    private AdminPaymentResponse toAdminResponse(Payment payment) {
        Order order = payment.getOrder();
        boolean refundPending = payment.getMethod() == PaymentMethod.RAZORPAY
                && payment.getStatus() == PaymentStatus.PAID && order.getStatus() == OrderStatus.CANCELLED;
        return new AdminPaymentResponse(payment.getId(), order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getUser().getFullName(), order.getUser().getEmail(), payment.getMethod(), payment.getStatus(),
                payment.getAmount(), payment.getCurrency(), payment.getProviderMethod(), payment.getRazorpayOrderId(),
                payment.getRazorpayPaymentId(), payment.getFailureReason(), payment.getRefundId(),
                payment.getRefundedAmount(), refundPending, payment.getPaidAt(), payment.getCreatedAt());
    }

    private static ApiException verificationFailed(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_VERIFICATION_FAILED, message);
    }

    /**
     * Contact number pre-filled in Razorpay Checkout: the order's delivery phone (the freshest one),
     * else the account phone, without spaces or dashes ("+91 98450 12345" -> "+919845012345").
     */
    static String razorpayContact(String deliveryPhone, String accountPhone) {
        String phone = StringUtils.hasText(deliveryPhone) ? deliveryPhone : accountPhone;
        return StringUtils.hasText(phone) ? phone.replaceAll("[\\s-]", "") : null;
    }

    private record PendingPayment(Long paymentId, String razorpayOrderId, Long orderId, String orderNumber,
                                  long amountPaise, String currency, String name, String email, String phone) {
    }
}

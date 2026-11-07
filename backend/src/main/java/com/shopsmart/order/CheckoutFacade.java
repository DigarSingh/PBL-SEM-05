package com.shopsmart.order;

import com.shopsmart.common.exception.ApiException;
import com.shopsmart.order.dto.CheckoutRequest;
import com.shopsmart.order.dto.CheckoutResponse;
import com.shopsmart.order.dto.OrderDetailResponse;
import com.shopsmart.order.dto.RazorpayCheckoutResponse;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentService;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates checkout: idempotency (the same Idempotency-Key never creates two orders), the
 * transactional order creation, and - for online payment - creating the Razorpay order after the
 * local transaction has committed, so no locks are held during the network call.
 */
@Service
public class CheckoutFacade {

    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final OrderQueryService queryService;
    private final TransactionTemplate tx;

    public CheckoutFacade(CheckoutService checkoutService, PaymentService paymentService,
                          OrderRepository orderRepository, OrderQueryService queryService, TransactionTemplate tx) {
        this.checkoutService = checkoutService;
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
        this.queryService = queryService;
        this.tx = tx;
    }

    public CheckoutResponse checkout(Long userId, CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("A valid Idempotency-Key header (8-64 characters) is required");
        }
        CheckoutService.Result result;
        try {
            result = checkoutService.createOrder(userId, request, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key committed first: return its order.
            Order existing = tx.execute(status -> orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElse(null));
            if (existing == null) {
                throw e;
            }
            return respond(existing.getId(), userId, true);
        }
        Order order = result.order();
        if (!result.existing() && order.getPaymentMethod() == PaymentMethod.RAZORPAY) {
            try {
                paymentService.startRazorpayPayment(order.getId(), userId);
            } catch (ApiException e) {
                paymentService.cancelAfterGatewayFailure(order.getId());
                throw e;
            }
        }
        return respond(order.getId(), userId, result.existing());
    }

    /** Payment for an existing unpaid order (first attempt after a reload, or a retry). */
    public RazorpayCheckoutResponse payExisting(Long userId, Long orderId) {
        return paymentService.startRazorpayPayment(orderId, userId);
    }

    private CheckoutResponse respond(Long orderId, Long userId, boolean existing) {
        OrderDetailResponse order = queryService.customerOrder(userId, orderId);
        RazorpayCheckoutResponse razorpay = order.canPay() ? paymentService.startRazorpayPayment(orderId, userId) : null;
        return new CheckoutResponse(order, razorpay, existing);
    }
}

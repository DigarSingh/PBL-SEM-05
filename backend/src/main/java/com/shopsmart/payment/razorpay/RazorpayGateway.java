package com.shopsmart.payment.razorpay;

import java.util.List;
import java.util.Map;

/**
 * Razorpay REST operations used by the store (Orders, Payments, Refunds APIs). Implementations
 * throw {@link PaymentGatewayException} for any gateway failure.
 */
public interface RazorpayGateway {

    RazorpayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);

    RazorpayPayment fetchPayment(String paymentId);

    List<RazorpayPayment> fetchOrderPayments(String razorpayOrderId);

    RazorpayPayment capture(String paymentId, long amountPaise, String currency);

    RazorpayRefund refund(String paymentId, long amountPaise, Map<String, String> notes);

    record RazorpayOrder(String id, long amount, String currency, String status) {
    }

    record RazorpayPayment(String id, String orderId, String status, long amount, String currency, String method,
                           String errorCode, String errorDescription) {

        public boolean isCaptured() {
            return "captured".equals(status);
        }

        public boolean isAuthorized() {
            return "authorized".equals(status);
        }

        public boolean isFailed() {
            return "failed".equals(status);
        }
    }

    record RazorpayRefund(String id, String paymentId, long amount, String status) {
    }
}

package com.shopsmart.payment.dto;

import com.shopsmart.order.OrderStatus;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminPaymentResponse(
        Long id,
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        String customerName,
        String customerEmail,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String providerMethod,
        String razorpayOrderId,
        String razorpayPaymentId,
        String failureReason,
        String refundId,
        BigDecimal refundedAmount,
        boolean refundPending,
        Instant paidAt,
        Instant createdAt) {
}

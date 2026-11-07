package com.shopsmart.order.dto;

import com.shopsmart.order.OrderStatus;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderSummaryResponse(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        String customerEmail,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        int itemCount,
        Instant createdAt,
        Instant placedAt) {
}

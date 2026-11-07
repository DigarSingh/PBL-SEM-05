package com.shopsmart.order.dto;

import com.shopsmart.order.OrderStatus;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        int itemCount,
        String firstItemName,
        String firstItemImage,
        Instant createdAt,
        Instant placedAt) {
}

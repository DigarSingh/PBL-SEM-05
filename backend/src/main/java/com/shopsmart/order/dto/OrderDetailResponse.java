package com.shopsmart.order.dto;

import com.shopsmart.order.OrderStatus;
import com.shopsmart.payment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record OrderDetailResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentInfo payment,
        List<OrderItemResponse> items,
        BigDecimal mrpTotal,
        BigDecimal subtotal,
        String couponCode,
        BigDecimal couponDiscount,
        BigDecimal shippingFee,
        BigDecimal taxRate,
        boolean taxInclusive,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        ShippingAddressResponse shippingAddress,
        String customerNote,
        LocalDate estimatedDeliveryDate,
        Instant createdAt,
        Instant placedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        String cancellationReason,
        List<TimelineStep> timeline,
        List<StatusHistoryResponse> history,
        boolean canCancel,
        boolean canPay,
        Instant paymentDeadline,
        CustomerInfo customer,
        Set<OrderStatus> allowedTransitions) {

    /** Only populated for administrators. */
    public record CustomerInfo(Long id, String fullName, String email, String phone) {
    }
}

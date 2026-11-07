package com.shopsmart.payment.events;

public record PaymentRefundedEvent(Long orderId, Long paymentId) {
}

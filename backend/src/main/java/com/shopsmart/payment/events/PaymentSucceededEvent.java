package com.shopsmart.payment.events;

public record PaymentSucceededEvent(Long orderId, Long paymentId) {
}

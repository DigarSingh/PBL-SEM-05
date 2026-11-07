package com.shopsmart.payment.events;

public record PaymentFailedEvent(Long orderId, String reason) {
}

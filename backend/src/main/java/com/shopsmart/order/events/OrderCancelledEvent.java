package com.shopsmart.order.events;

/** Published after an order is cancelled; payment handling refunds it if money was captured. */
public record OrderCancelledEvent(Long orderId, Long paymentId, String reason) {
}

package com.shopsmart.realtime;

import java.time.Instant;

/** Pushed to /topic/orders/{id}, the owner's /user/queue/orders and /topic/admin/orders. */
public record OrderUpdateMessage(String type, Long orderId, String orderNumber, String status, String statusLabel,
                                 String paymentStatus, String message, Instant at) {
}

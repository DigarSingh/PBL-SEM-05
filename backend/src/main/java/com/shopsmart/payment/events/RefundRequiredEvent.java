package com.shopsmart.payment.events;

/** A payment was captured for an order that is already cancelled (e.g. paid after expiry). */
public record RefundRequiredEvent(Long paymentId) {
}

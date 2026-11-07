package com.shopsmart.payment;

/**
 * PENDING: awaiting payment (online) or collection (COD). AUTHORIZED: funds authorised but not
 * yet captured. PAID: captured/collected. FAILED: the last attempt failed. REFUNDED: money
 * returned. CANCELLED: the order was cancelled before any money moved.
 */
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    PAID,
    FAILED,
    REFUNDED,
    CANCELLED;

    public boolean isSettled() {
        return this == AUTHORIZED || this == PAID;
    }
}

package com.shopsmart.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. {@code PENDING_PAYMENT} precedes {@code PLACED} for online payments: the
 * order exists (holding a stock reservation) but has not been paid, so it never enters the
 * fulfilment pipeline. Transitions are strictly forward; any other change is rejected.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PLACED,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAYMENT, EnumSet.of(PLACED, CANCELLED),
            PLACED, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PROCESSING, CANCELLED),
            PROCESSING, EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED, EnumSet.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public Set<OrderStatus> allowedTransitions() {
        return Set.copyOf(TRANSITIONS.get(this));
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Orders whose stock is committed (sold) rather than merely reserved or released. */
    public boolean holdsCommittedStock() {
        return this == PLACED || this == CONFIRMED || this == PROCESSING;
    }

    /** Customers may cancel their own order until it is being processed. */
    public boolean isCancellableByCustomer() {
        return this == PENDING_PAYMENT || this == PLACED || this == CONFIRMED;
    }
}

package com.shopsmart.order.dto;

import com.shopsmart.order.OrderStatus;
import java.time.Instant;

/** One step of the customer-facing progress tracker. */
public record TimelineStep(OrderStatus status, String label, boolean reached, Instant at) {
}

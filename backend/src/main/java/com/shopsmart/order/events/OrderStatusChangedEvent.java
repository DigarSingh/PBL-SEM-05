package com.shopsmart.order.events;

import com.shopsmart.order.OrderStatus;

public record OrderStatusChangedEvent(Long orderId, OrderStatus from, OrderStatus to, String note) {
}

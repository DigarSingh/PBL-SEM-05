package com.shopsmart.order.dto;

import com.shopsmart.order.OrderStatus;
import java.time.Instant;

public record StatusHistoryResponse(OrderStatus fromStatus, OrderStatus toStatus, String note, Long changedByUserId,
                                    String changedBy, Instant at) {
}

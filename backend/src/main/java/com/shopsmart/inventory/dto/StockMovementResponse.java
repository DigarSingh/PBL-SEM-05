package com.shopsmart.inventory.dto;

import com.shopsmart.inventory.StockMovementType;
import java.time.Instant;

public record StockMovementResponse(
        Long id,
        StockMovementType type,
        int quantityChange,
        int reservedChange,
        int quantityAfter,
        int reservedAfter,
        Long orderId,
        Long actorUserId,
        String note,
        Instant createdAt) {
}

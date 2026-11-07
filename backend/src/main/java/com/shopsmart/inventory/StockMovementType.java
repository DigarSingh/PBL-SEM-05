package com.shopsmart.inventory;

public enum StockMovementType {
    INITIAL,
    RESTOCK,
    ADJUSTMENT,
    SALE,
    RESERVATION,
    RESERVATION_COMMITTED,
    RESERVATION_RELEASED,
    CANCELLATION_RESTOCK
}

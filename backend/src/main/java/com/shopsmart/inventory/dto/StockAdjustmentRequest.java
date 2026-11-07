package com.shopsmart.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** type RESTOCK adds units received; ADJUSTMENT corrects stock up or down (e.g. damage, counts). */
public record StockAdjustmentRequest(
        @NotNull Type type,
        @Min(-1_000_000) @Max(1_000_000) int quantity,
        @Size(max = 255) String note) {

    public enum Type {
        RESTOCK,
        ADJUSTMENT
    }
}

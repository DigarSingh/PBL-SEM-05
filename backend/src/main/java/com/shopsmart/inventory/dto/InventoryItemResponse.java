package com.shopsmart.inventory.dto;

import com.shopsmart.product.StockStatus;
import java.time.Instant;

public record InventoryItemResponse(
        Long variantId,
        String sku,
        String variantName,
        Long productId,
        String productName,
        String categoryName,
        boolean variantActive,
        int quantity,
        int reserved,
        int available,
        int lowStockThreshold,
        StockStatus stockStatus,
        Instant updatedAt) {
}

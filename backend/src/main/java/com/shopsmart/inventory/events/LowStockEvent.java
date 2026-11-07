package com.shopsmart.inventory.events;

/** Published when a variant's available stock falls to or below its low-stock threshold. */
public record LowStockEvent(Long variantId, Long productId, String productName, String sku, int available,
                            int threshold) {
}

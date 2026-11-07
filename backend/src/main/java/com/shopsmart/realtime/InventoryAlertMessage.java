package com.shopsmart.realtime;

import java.time.Instant;

public record InventoryAlertMessage(Long variantId, Long productId, String productName, String sku, int available,
                                    int threshold, Instant at) {
}

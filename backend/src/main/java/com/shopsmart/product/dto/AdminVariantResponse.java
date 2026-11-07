package com.shopsmart.product.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminVariantResponse(
        Long id,
        String sku,
        String name,
        Map<String, String> attributes,
        BigDecimal priceOverride,
        BigDecimal listPrice,
        BigDecimal salePrice,
        boolean active,
        int quantity,
        int reserved,
        int available,
        int lowStockThreshold,
        boolean hasOrders) {
}

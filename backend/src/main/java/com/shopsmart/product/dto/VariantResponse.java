package com.shopsmart.product.dto;

import com.shopsmart.product.StockStatus;
import java.math.BigDecimal;
import java.util.Map;

public record VariantResponse(
        Long id,
        String sku,
        String name,
        Map<String, String> attributes,
        BigDecimal price,
        BigDecimal salePrice,
        boolean inStock,
        StockStatus stockStatus,
        Integer lowStockQuantity) {
}

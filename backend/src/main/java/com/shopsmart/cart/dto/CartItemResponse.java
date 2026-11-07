package com.shopsmart.cart.dto;

import com.shopsmart.product.StockStatus;
import java.math.BigDecimal;
import java.util.Map;

/** {@code issue} explains why a line blocks checkout (OUT_OF_STOCK, INSUFFICIENT_STOCK, UNAVAILABLE). */
public record CartItemResponse(
        Long id,
        Long variantId,
        Long productId,
        String productName,
        String productSlug,
        String brandName,
        String variantName,
        String sku,
        Map<String, String> attributes,
        String imageUrl,
        BigDecimal unitMrp,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        int available,
        int maxQuantity,
        StockStatus stockStatus,
        String issue) {
}

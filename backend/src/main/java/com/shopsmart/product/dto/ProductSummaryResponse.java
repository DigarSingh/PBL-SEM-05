package com.shopsmart.product.dto;

import com.shopsmart.product.StockStatus;
import java.math.BigDecimal;

/** Everything a product card needs. Prices are the cheapest active variant's. */
public record ProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BrandRef brand,
        CategoryRef category,
        String imageUrl,
        String thumbnailUrl,
        BigDecimal price,
        BigDecimal salePrice,
        BigDecimal discountPercent,
        BigDecimal averageRating,
        int reviewCount,
        boolean inStock,
        StockStatus stockStatus,
        Integer lowStockQuantity,
        boolean featured,
        Long defaultVariantId,
        int variantCount) {
}

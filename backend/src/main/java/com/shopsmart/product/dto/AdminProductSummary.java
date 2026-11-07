package com.shopsmart.product.dto;

import com.shopsmart.product.ProductStatus;
import com.shopsmart.product.StockStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminProductSummary(
        Long id,
        String name,
        String slug,
        String imageUrl,
        BrandRef brand,
        CategoryRef category,
        ProductStatus status,
        boolean featured,
        BigDecimal price,
        BigDecimal salePrice,
        BigDecimal discountPercent,
        int totalAvailable,
        StockStatus stockStatus,
        int variantCount,
        int soldCount,
        BigDecimal averageRating,
        Instant updatedAt) {
}

package com.shopsmart.product.dto;

import com.shopsmart.product.StockStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String slug,
        BrandRef brand,
        CategoryRef category,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal salePrice,
        BigDecimal discountPercent,
        BigDecimal averageRating,
        int reviewCount,
        int soldCount,
        boolean featured,
        boolean inStock,
        StockStatus stockStatus,
        List<String> tags,
        List<SpecificationDto> specifications,
        List<ImageResponse> images,
        List<VariantResponse> variants,
        List<VariantOption> options,
        Long defaultVariantId,
        Instant createdAt) {
}

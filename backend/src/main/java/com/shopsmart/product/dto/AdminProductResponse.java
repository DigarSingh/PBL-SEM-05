package com.shopsmart.product.dto;

import com.shopsmart.product.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminProductResponse(
        Long id,
        String name,
        String slug,
        BrandRef brand,
        CategoryRef category,
        String shortDescription,
        String description,
        BigDecimal price,
        BigDecimal discountPercent,
        BigDecimal salePrice,
        ProductStatus status,
        boolean featured,
        BigDecimal averageRating,
        int reviewCount,
        int soldCount,
        List<String> tags,
        List<SpecificationDto> specifications,
        List<ImageResponse> images,
        List<AdminVariantResponse> variants,
        Instant createdAt,
        Instant updatedAt) {
}

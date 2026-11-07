package com.shopsmart.recommendation.dto;

import com.shopsmart.product.dto.ProductSummaryResponse;

public record RecommendedProduct(ProductSummaryResponse product, String reason) {
}

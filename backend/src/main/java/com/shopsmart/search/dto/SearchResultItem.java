package com.shopsmart.search.dto;

import com.shopsmart.product.dto.ProductSummaryResponse;
import java.util.List;

public record SearchResultItem(ProductSummaryResponse product, double score, List<String> matchReasons) {
}

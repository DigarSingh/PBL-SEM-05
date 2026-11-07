package com.shopsmart.search;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured interpretation of a natural-language query, e.g. "gaming headphones under 5000
 * with wireless" -> category Audio, useCases [gaming], features [wireless], maxPrice 5000.
 * {@code semanticText} is what remains for embedding-based ranking.
 */
public record ParsedQuery(
        String original,
        String semanticText,
        Long categoryId,
        String categoryName,
        String categorySlug,
        boolean categoryStrong,
        String brandSlug,
        String brandName,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        boolean inStockOnly,
        List<String> features,
        List<String> useCases) {
}

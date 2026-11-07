package com.shopsmart.search.dto;

import java.math.BigDecimal;
import java.util.List;

/** What the smart search understood, shown to shoppers as removable filter chips. */
public record SearchInterpretation(
        String category,
        String categorySlug,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        boolean inStockOnly,
        List<String> features,
        List<String> useCases,
        boolean filtersRelaxed) {
}

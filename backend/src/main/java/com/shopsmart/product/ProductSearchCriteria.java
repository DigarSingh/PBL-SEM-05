package com.shopsmart.product;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * Filters for product queries. {@code storefront} restricts results to what customers may see
 * (active products whose category and brand are active).
 */
@Builder(toBuilder = true)
public record ProductSearchCriteria(
        String keywords,
        Long categoryId,
        String categorySlug,
        List<String> brandSlugs,
        Long brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        BigDecimal minDiscount,
        Boolean featured,
        boolean inStockOnly,
        Set<ProductStatus> statuses,
        boolean storefront,
        Collection<Long> productIds,
        Collection<Long> excludeIds,
        ProductSort sort) {

    public static ProductSearchCriteriaBuilder storefrontDefaults() {
        return builder().storefront(true).statuses(Set.of(ProductStatus.ACTIVE)).sort(ProductSort.POPULARITY);
    }
}

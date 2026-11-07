package com.shopsmart.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductFacetsResponse(List<BrandFacet> brands, BigDecimal minPrice, BigDecimal maxPrice) {

    public record BrandFacet(Long id, String name, String slug, long count) {
    }
}

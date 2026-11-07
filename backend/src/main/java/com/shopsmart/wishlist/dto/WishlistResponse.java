package com.shopsmart.wishlist.dto;

import com.shopsmart.product.dto.ProductSummaryResponse;
import java.time.Instant;
import java.util.List;

public record WishlistResponse(List<Item> items, int count) {

    public record Item(ProductSummaryResponse product, Instant addedAt) {
    }
}

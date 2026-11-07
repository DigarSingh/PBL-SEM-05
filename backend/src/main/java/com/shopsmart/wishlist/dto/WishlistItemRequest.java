package com.shopsmart.wishlist.dto;

import jakarta.validation.constraints.NotNull;

public record WishlistItemRequest(@NotNull Long productId) {
}

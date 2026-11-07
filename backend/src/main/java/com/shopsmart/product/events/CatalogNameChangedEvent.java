package com.shopsmart.product.events;

/** A brand or category was renamed: the search documents of its products must be rebuilt. */
public record CatalogNameChangedEvent(Long brandId, Long categoryId) {
}

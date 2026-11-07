package com.shopsmart.product.events;

/** The sellable catalog changed (product created/updated/archived); consumers may refresh caches. */
public record ProductCatalogChangedEvent(Long productId) {
}

package com.shopsmart.activity;

/**
 * Behavioural events used as recommendation signals, with their relative weight.
 * SEARCH and PRODUCT_VIEW are also mirrored in search_history / product_views, which
 * additionally capture anonymous traffic for analytics.
 */
public enum ActivityType {
    PRODUCT_VIEW(1.0),
    SEARCH(1.0),
    CATEGORY_VIEW(0.5),
    ADD_TO_CART(3.0),
    REMOVE_FROM_CART(-1.0),
    WISHLIST_ADD(3.0),
    WISHLIST_REMOVE(-1.5),
    PURCHASE(5.0),
    REVIEW(2.0);

    private final double weight;

    ActivityType(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}

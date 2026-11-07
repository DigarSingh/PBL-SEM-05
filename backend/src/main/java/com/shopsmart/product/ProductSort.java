package com.shopsmart.product;

import java.util.Locale;

public enum ProductSort {
    RELEVANCE,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    RATING,
    POPULARITY,
    DISCOUNT;

    /** Accepts e.g. "price_asc", "price-asc" or "PRICE_ASC"; unknown values fall back. */
    public static ProductSort parse(String value, ProductSort fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

package com.shopsmart.seed;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Shape of {@code classpath:seed/catalog.json}. */
public record SeedCatalog(List<Category> categories, List<Brand> brands, List<Product> products, List<Coupon> coupons) {

    public record Category(String name, String slug, String description, String image) {
    }

    public record Brand(String name, String description) {
    }

    public record Product(String name, String brand, String category, BigDecimal price, BigDecimal discount,
                          Boolean featured, @JsonProperty("short") String shortDescription, String description,
                          List<String> tags, List<List<String>> specs, List<String> images, List<Variant> variants,
                          double demand, String trend, double quality) {
    }

    public record Variant(String sku, String name, Map<String, String> attributes, BigDecimal price, int stock,
                          Integer threshold) {
    }

    public record Coupon(String code, String description, String type, BigDecimal value, BigDecimal minOrder,
                         BigDecimal maxDiscount, Integer usageLimit, Integer perUserLimit, Integer expiresInDays,
                         Boolean active) {
    }
}

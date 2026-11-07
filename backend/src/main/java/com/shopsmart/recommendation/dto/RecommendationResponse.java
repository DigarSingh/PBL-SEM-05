package com.shopsmart.recommendation.dto;

import java.util.List;

/** {@code strategy} tells the client how results were produced (e.g. "personalized", "trending"). */
public record RecommendationResponse(String strategy, List<RecommendedProduct> items) {
}

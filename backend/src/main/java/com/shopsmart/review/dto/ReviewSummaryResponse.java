package com.shopsmart.review.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ReviewSummaryResponse(BigDecimal averageRating, long reviewCount, Map<Integer, Long> distribution) {
}

package com.shopsmart.review.dto;

import java.time.Instant;

/** Public review. The author is shown as first name + last initial only. */
public record ReviewResponse(Long id, int rating, String title, String comment, String authorName,
                             boolean verifiedPurchase, boolean mine, Instant createdAt, Instant updatedAt) {
}

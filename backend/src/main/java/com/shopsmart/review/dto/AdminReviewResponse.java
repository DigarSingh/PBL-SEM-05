package com.shopsmart.review.dto;

import com.shopsmart.review.ReviewStatus;
import java.time.Instant;

public record AdminReviewResponse(Long id, Long productId, String productName, Long userId, String userName,
                                  String userEmail, int rating, String title, String comment, ReviewStatus status,
                                  String moderationNote, Instant createdAt) {
}

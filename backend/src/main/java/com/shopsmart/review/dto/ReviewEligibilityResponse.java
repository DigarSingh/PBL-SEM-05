package com.shopsmart.review.dto;

public record ReviewEligibilityResponse(boolean canReview, String reason, ReviewResponse existingReview) {
}

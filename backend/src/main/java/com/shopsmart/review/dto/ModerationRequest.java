package com.shopsmart.review.dto;

import com.shopsmart.review.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModerationRequest(@NotNull ReviewStatus status, @Size(max = 255) String note) {
}

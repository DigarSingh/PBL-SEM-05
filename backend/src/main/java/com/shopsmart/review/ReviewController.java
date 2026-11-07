package com.shopsmart.review;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.review.dto.AdminReviewResponse;
import com.shopsmart.review.dto.ModerationRequest;
import com.shopsmart.review.dto.ReviewEligibilityResponse;
import com.shopsmart.review.dto.ReviewRequest;
import com.shopsmart.review.dto.ReviewResponse;
import com.shopsmart.review.dto.ReviewSummaryResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews", description = "Verified-purchase reviews and moderation")
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Published reviews of a product", description = "sort: newest, highest, lowest")
    @GetMapping("/api/products/{productId}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> list(@PathVariable Long productId,
                                                          @CurrentUser(required = false) AuthUser user,
                                                          @RequestParam(required = false) String sort,
                                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                                          @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(reviewService.list(productId, user == null ? null : user.id(), sort, page, size));
    }

    @Operation(summary = "Rating average and 1-5 star distribution")
    @GetMapping("/api/products/{productId}/reviews/summary")
    public ApiResponse<ReviewSummaryResponse> summary(@PathVariable Long productId) {
        return ApiResponse.ok(reviewService.summary(productId));
    }

    @Operation(summary = "Whether I may review this product")
    @GetMapping("/api/products/{productId}/reviews/eligibility")
    public ApiResponse<ReviewEligibilityResponse> eligibility(@CurrentUser AuthUser user, @PathVariable Long productId) {
        return ApiResponse.ok(reviewService.eligibility(user.id(), productId));
    }

    @Operation(summary = "Review a product I received")
    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(@CurrentUser AuthUser user, @PathVariable Long productId,
                                                              @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(reviewService.create(user.id(), productId, request), "Thanks for your review!"));
    }

    @Operation(summary = "Edit my review")
    @PutMapping("/api/reviews/{id}")
    public ApiResponse<ReviewResponse> update(@CurrentUser AuthUser user, @PathVariable Long id,
                                              @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(reviewService.update(user.id(), id, request), "Review updated");
    }

    @Operation(summary = "Delete my review")
    @DeleteMapping("/api/reviews/{id}")
    public ApiResponse<Void> delete(@CurrentUser AuthUser user, @PathVariable Long id) {
        reviewService.delete(user.id(), id);
        return ApiResponse.message("Review deleted");
    }

    @Operation(summary = "Admin: search reviews")
    @GetMapping("/api/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<AdminReviewResponse>> search(@RequestParam(required = false) ReviewStatus status,
                                                                 @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
                                                                 @RequestParam(required = false) Long productId,
                                                                 @RequestParam(required = false) String q,
                                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(reviewService.search(status, rating, productId, q, page, size));
    }

    @Operation(summary = "Admin: publish or hide a review")
    @PatchMapping("/api/admin/reviews/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminReviewResponse> moderate(@PathVariable Long id, @Valid @RequestBody ModerationRequest request) {
        return ApiResponse.ok(reviewService.moderate(id, request.status(), request.note()), "Review updated");
    }
}

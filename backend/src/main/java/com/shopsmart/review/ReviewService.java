package com.shopsmart.review;

import com.shopsmart.activity.ActivityService;
import com.shopsmart.activity.ActivityType;
import com.shopsmart.admin.AuditService;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.order.OrderRepository;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductRepository;
import com.shopsmart.product.ProductService;
import com.shopsmart.review.dto.AdminReviewResponse;
import com.shopsmart.review.dto.ReviewEligibilityResponse;
import com.shopsmart.review.dto.ReviewRequest;
import com.shopsmart.review.dto.ReviewResponse;
import com.shopsmart.review.dto.ReviewSummaryResponse;
import com.shopsmart.user.User;
import com.shopsmart.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Reviews from verified purchasers (a delivered order containing the product), one per product
 * per customer. Product rating aggregates are recomputed from published reviews on every change.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final AuditService auditService;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository,
                         OrderRepository orderRepository, UserRepository userRepository,
                         ActivityService activityService, AuditService auditService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> list(Long productId, Long viewerId, String sort, int page, int size) {
        Sort order = switch (sort == null ? "newest" : sort.toLowerCase(Locale.ROOT)) {
            case "highest" -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "lowest" -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        return PageResponse.from(reviewRepository.findByProduct(productId, ReviewStatus.PUBLISHED,
                PageRequest.of(page, size, order)), review -> toResponse(review, viewerId));
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long productId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            distribution.put(stars, 0L);
        }
        for (Object[] row : reviewRepository.distribution(productId)) {
            distribution.put((Integer) row[0], (Long) row[1]);
        }
        Object[] aggregate = reviewRepository.aggregate(productId).getFirst();
        long count = (Long) aggregate[1];
        BigDecimal average = count == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((Double) aggregate[0]).setScale(2, RoundingMode.HALF_UP);
        return new ReviewSummaryResponse(average, count, distribution);
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse eligibility(Long userId, Long productId) {
        var existing = reviewRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            return new ReviewEligibilityResponse(false, "You have already reviewed this product",
                    toResponse(existing.get(), userId));
        }
        if (orderRepository.findDeliveredOrderIdsContaining(userId, productId).isEmpty()) {
            return new ReviewEligibilityResponse(false, "Only customers who received this product can review it", null);
        }
        return new ReviewEligibilityResponse(true, null, null);
    }

    @Transactional
    public ReviewResponse create(Long userId, Long productId, ReviewRequest request) {
        Product product = productRepository.findWithBrandAndCategory(productId)
                .filter(ProductService::isStorefrontVisible)
                .orElseThrow(() -> ApiException.notFound("Product"));
        if (reviewRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            throw ApiException.duplicate("You have already reviewed this product. Edit your existing review instead.");
        }
        List<Long> deliveredOrders = orderRepository.findDeliveredOrderIdsContaining(userId, productId);
        if (deliveredOrders.isEmpty()) {
            throw ApiException.forbidden("Only customers who received this product can review it");
        }
        User user = userRepository.getReferenceById(userId);
        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setOrderId(deliveredOrders.getFirst());
        apply(review, request);
        reviewRepository.saveAndFlush(review);
        refreshAggregate(productId);
        activityService.record(userId, ActivityType.REVIEW, productId, product.getCategory().getId(),
                Map.of("rating", request.rating()));
        return toResponse(review, userId);
    }

    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewRequest request) {
        Review review = loadOwned(userId, reviewId);
        apply(review, request);
        reviewRepository.flush();
        refreshAggregate(review.getProduct().getId());
        return toResponse(review, userId);
    }

    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = loadOwned(userId, reviewId);
        Long productId = review.getProduct().getId();
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshAggregate(productId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> search(ReviewStatus status, Integer rating, Long productId, String q,
                                                    int page, int size) {
        String pattern = StringUtils.hasText(q) ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : null;
        return PageResponse.from(reviewRepository.search(status, rating, productId, pattern,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), ReviewService::toAdmin);
    }

    @Transactional
    public AdminReviewResponse moderate(Long reviewId, ReviewStatus status, String note) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> ApiException.notFound("Review"));
        ReviewStatus previous = review.getStatus();
        review.setStatus(status);
        review.setModerationNote(StringUtils.hasText(note) ? note.trim() : null);
        reviewRepository.flush();
        refreshAggregate(review.getProduct().getId());
        auditService.record("REVIEW_MODERATED", "REVIEW", reviewId,
                Map.of("from", previous.name(), "to", status.name(), "note", note == null ? "" : note));
        return toAdmin(review);
    }

    private void refreshAggregate(Long productId) {
        Object[] aggregate = reviewRepository.aggregate(productId).getFirst();
        long count = (Long) aggregate[1];
        BigDecimal average = count == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((Double) aggregate[0]).setScale(2, RoundingMode.HALF_UP);
        productRepository.updateRating(productId, average, (int) count);
    }

    private Review loadOwned(Long userId, Long reviewId) {
        return reviewRepository.findById(reviewId)
                .filter(review -> review.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Review"));
    }

    private static void apply(Review review, ReviewRequest request) {
        review.setRating(request.rating());
        review.setTitle(request.title().trim());
        review.setComment(StringUtils.hasText(request.comment()) ? request.comment().trim() : null);
    }

    static String displayName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }

    private static ReviewResponse toResponse(Review review, Long viewerId) {
        return new ReviewResponse(review.getId(), review.getRating(), review.getTitle(), review.getComment(),
                displayName(review.getUser().getFullName()), review.getOrderId() != null,
                viewerId != null && viewerId.equals(review.getUser().getId()), review.getCreatedAt(),
                review.getUpdatedAt());
    }

    private static AdminReviewResponse toAdmin(Review review) {
        return new AdminReviewResponse(review.getId(), review.getProduct().getId(), review.getProduct().getName(),
                review.getUser().getId(), review.getUser().getFullName(), review.getUser().getEmail(),
                review.getRating(), review.getTitle(), review.getComment(), review.getStatus(),
                review.getModerationNote(), review.getCreatedAt());
    }
}

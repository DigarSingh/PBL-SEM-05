package com.shopsmart.coupon;

import com.shopsmart.admin.AuditService;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.exception.ErrorCode;
import com.shopsmart.common.util.Money;
import com.shopsmart.coupon.dto.CouponRequest;
import com.shopsmart.coupon.dto.CouponResponse;
import com.shopsmart.coupon.dto.OfferResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;
    private final AuditService auditService;
    private final Clock clock;

    public CouponService(CouponRepository couponRepository, CouponUsageRepository usageRepository,
                         AuditService auditService, Clock clock) {
        this.couponRepository = couponRepository;
        this.usageRepository = usageRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    public static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    /** Read-only evaluation for cart display. */
    @Transactional(readOnly = true)
    public CouponEvaluation evaluate(String rawCode, Long userId, BigDecimal subtotal) {
        String code = normalize(rawCode);
        return couponRepository.findByCode(code)
                .map(coupon -> CouponRules.evaluate(coupon, subtotal,
                        usageRepository.countByCouponIdAndUserId(coupon.getId(), userId), clock.instant()))
                .orElse(CouponEvaluation.invalid(code, "This coupon code is not valid"));
    }

    /**
     * Checkout step 1: locks the coupon row (so concurrent orders cannot exceed usage limits) and
     * re-validates it. Must run inside the checkout transaction, which uses READ COMMITTED so the
     * per-user usage count reflects redemptions committed while waiting for the lock.
     */
    @Transactional
    public CouponEvaluation lockForCheckout(String rawCode, Long userId, BigDecimal subtotal) {
        String code = normalize(rawCode);
        Coupon coupon = couponRepository.lockByCode(code)
                .orElseThrow(() -> invalidCoupon("This coupon code is not valid"));
        CouponEvaluation evaluation = CouponRules.evaluate(coupon, subtotal,
                usageRepository.countByCouponIdAndUserId(coupon.getId(), userId), clock.instant());
        if (!evaluation.valid()) {
            throw invalidCoupon(evaluation.message());
        }
        return evaluation;
    }

    /** Checkout step 2: records the redemption for the created order (same transaction as step 1). */
    @Transactional
    public void recordRedemption(CouponEvaluation evaluation, Long userId, Long orderId) {
        Coupon coupon = evaluation.coupon();
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        CouponUsage usage = new CouponUsage();
        usage.setCoupon(coupon);
        usage.setUserId(userId);
        usage.setOrderId(orderId);
        usage.setDiscountAmount(evaluation.discount());
        usageRepository.save(usage);
    }

    /** Frees a redemption again when its order is cancelled or never paid. */
    @Transactional
    public void releaseForOrder(Long orderId) {
        usageRepository.findByOrderId(orderId).ifPresent(usage -> {
            Coupon coupon = couponRepository.lockByCode(usage.getCoupon().getCode()).orElseThrow();
            coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
            usageRepository.delete(usage);
        });
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> currentOffers() {
        return couponRepository.findCurrentlyValid(clock.instant()).stream()
                .map(c -> new OfferResponse(c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                        c.getMinOrderAmount(), c.getMaxDiscountAmount(), c.getExpiresAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> search(String q, Boolean active, int page, int size) {
        String pattern = StringUtils.hasText(q) ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : null;
        return PageResponse.from(couponRepository.search(pattern, active,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), CouponService::toResponse);
    }

    @Transactional
    public CouponResponse create(CouponRequest request) {
        String code = normalize(request.code());
        if (couponRepository.existsByCode(code)) {
            throw ApiException.duplicate("A coupon with this code already exists");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        apply(coupon, request);
        couponRepository.save(coupon);
        auditService.record("COUPON_CREATED", "COUPON", coupon.getId(), Map.of("code", code));
        return toResponse(coupon);
    }

    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = load(id);
        String code = normalize(request.code());
        if (!coupon.getCode().equals(code)) {
            if (coupon.getUsedCount() > 0) {
                throw ApiException.businessRule("The code of a coupon that has been used cannot be changed");
            }
            if (couponRepository.existsByCode(code)) {
                throw ApiException.duplicate("A coupon with this code already exists");
            }
            coupon.setCode(code);
        }
        apply(coupon, request);
        auditService.record("COUPON_UPDATED", "COUPON", id, Map.of("code", coupon.getCode(),
                "active", coupon.isActive()));
        return toResponse(coupon);
    }

    /** Used coupons are kept for order history and can only be deactivated. */
    @Transactional
    public void delete(Long id) {
        Coupon coupon = load(id);
        if (coupon.getUsedCount() > 0) {
            throw ApiException.conflict("This coupon has been used. Deactivate it instead of deleting it.");
        }
        couponRepository.delete(coupon);
        auditService.record("COUPON_DELETED", "COUPON", id, Map.of("code", coupon.getCode()));
    }

    private Coupon load(Long id) {
        return couponRepository.findById(id).orElseThrow(() -> ApiException.notFound("Coupon"));
    }

    private static void apply(Coupon coupon, CouponRequest request) {
        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw ApiException.badRequest("A percentage discount cannot exceed 100%");
        }
        if (request.startsAt() != null && request.expiresAt() != null
                && !request.expiresAt().isAfter(request.startsAt())) {
            throw ApiException.badRequest("The expiry must be after the start date");
        }
        coupon.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(Money.of(request.discountValue()));
        coupon.setMinOrderAmount(request.minOrderAmount() == null ? null : Money.of(request.minOrderAmount()));
        coupon.setMaxDiscountAmount(request.maxDiscountAmount() == null ? null : Money.of(request.maxDiscountAmount()));
        coupon.setStartsAt(request.startsAt());
        coupon.setExpiresAt(request.expiresAt());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setPerUserLimit(request.perUserLimit());
        if (request.active() != null) {
            coupon.setActive(request.active());
        }
    }

    static ApiException invalidCoupon(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.INVALID_COUPON, message);
    }

    private static CouponResponse toResponse(Coupon c) {
        return new CouponResponse(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                c.getMinOrderAmount(), c.getMaxDiscountAmount(), c.getStartsAt(), c.getExpiresAt(), c.getUsageLimit(),
                c.getPerUserLimit(), c.getUsedCount(), c.isActive(), c.getCreatedAt());
    }
}

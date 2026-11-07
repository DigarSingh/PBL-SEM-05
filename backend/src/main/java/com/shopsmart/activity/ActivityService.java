package com.shopsmart.activity;

import com.shopsmart.product.Product;
import com.shopsmart.product.ProductRepository;
import com.shopsmart.product.ProductService;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records behavioural signals for recommendations and analytics. Only identifiers and small
 * context values are stored; never names, emails, addresses or free-form personal data.
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    private static final int MAX_METADATA = 1000;
    /** Repeat views of the same product by the same user within this window count once. */
    private static final Duration VIEW_DEDUP_WINDOW = Duration.ofMinutes(10);

    private final UserActivityRepository activityRepository;
    private final ProductViewRepository viewRepository;
    private final ProductRepository productRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ActivityService(UserActivityRepository activityRepository, ProductViewRepository viewRepository,
                           ProductRepository productRepository, JsonMapper jsonMapper, Clock clock) {
        this.activityRepository = activityRepository;
        this.viewRepository = viewRepository;
        this.productRepository = productRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(Long userId, ActivityType type, Long productId, Long categoryId, Map<String, ?> metadata) {
        if (userId == null) {
            return;
        }
        UserActivity activity = new UserActivity();
        activity.setUserId(userId);
        activity.setEventType(type);
        activity.setProductId(productId);
        activity.setCategoryId(categoryId);
        activity.setMetadata(serialize(metadata));
        activityRepository.save(activity);
    }

    /** Records a product page view (anonymous views too) and, for signed-in users, a PRODUCT_VIEW signal. */
    @Transactional
    public void recordProductView(Long productId, Long userId) {
        Product product = productRepository.findWithBrandAndCategory(productId)
                .filter(ProductService::isStorefrontVisible)
                .orElse(null);
        if (product == null) {
            return;
        }
        if (userId != null && activityRepository.existsByUserIdAndEventTypeAndProductIdAndCreatedAtAfter(
                userId, ActivityType.PRODUCT_VIEW, productId, clock.instant().minus(VIEW_DEDUP_WINDOW))) {
            return;
        }
        ProductView view = new ProductView();
        view.setProductId(productId);
        view.setUserId(userId);
        viewRepository.save(view);
        record(userId, ActivityType.PRODUCT_VIEW, productId, product.getCategory().getId(), null);
    }

    private String serialize(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            String json = jsonMapper.writeValueAsString(metadata);
            return json.length() > MAX_METADATA ? null : json;
        } catch (RuntimeException e) {
            log.debug("Dropping unserialisable activity metadata: {}", e.getMessage());
            return null;
        }
    }
}

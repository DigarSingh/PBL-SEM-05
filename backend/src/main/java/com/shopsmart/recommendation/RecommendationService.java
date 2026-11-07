package com.shopsmart.recommendation;

import com.shopsmart.activity.ActivityType;
import com.shopsmart.activity.UserActivity;
import com.shopsmart.activity.UserActivityRepository;
import com.shopsmart.ai.AiServiceClient;
import com.shopsmart.cart.CartRepository;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductRepository;
import com.shopsmart.product.ProductSearchCriteria;
import com.shopsmart.product.ProductSearchRepository;
import com.shopsmart.product.ProductService;
import com.shopsmart.product.ProductSort;
import com.shopsmart.product.dto.ProductSummaryResponse;
import com.shopsmart.order.OrderRepository;
import com.shopsmart.recommendation.dto.RecommendationResponse;
import com.shopsmart.recommendation.dto.RecommendedProduct;
import com.shopsmart.search.SearchHistoryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hybrid recommendations. Personalised results come from the AI service (content similarity to
 * what the user viewed/carted/bought/searched + collaborative filtering), cached per user until
 * the user produces new activity. Without the AI service, a category/brand-affinity model runs
 * in the backend; new or anonymous users get trending products. Unavailable or out-of-stock
 * products are never recommended.
 */
@Service
public class RecommendationService {

    private static final int SIGNAL_WINDOW_DAYS = 90;
    private static final Duration TRENDING_WINDOW = Duration.ofDays(14);
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final AiServiceClient aiClient;
    private final UserActivityRepository activityRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final RecommendationRepository cacheRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final ProductService productService;
    private final TrendingQuery trendingQuery;
    private final Clock clock;

    public RecommendationService(AiServiceClient aiClient, UserActivityRepository activityRepository,
                                 SearchHistoryRepository searchHistoryRepository,
                                 RecommendationRepository cacheRepository, CartRepository cartRepository,
                                 OrderRepository orderRepository, ProductRepository productRepository,
                                 ProductSearchRepository searchRepository,
                                 ProductService productService, TrendingQuery trendingQuery, Clock clock) {
        this.aiClient = aiClient;
        this.activityRepository = activityRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.cacheRepository = cacheRepository;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.searchRepository = searchRepository;
        this.productService = productService;
        this.trendingQuery = trendingQuery;
        this.clock = clock;
    }

    @Transactional
    public RecommendationResponse forYou(Long userId, int limit) {
        if (userId == null) {
            return trending(limit, "Trending right now");
        }
        List<UserActivity> activity = activityRepository.findRecentByUser(userId, PageRequest.of(0, 200)).stream()
                .filter(a -> a.getCreatedAt().isAfter(clock.instant().minus(Duration.ofDays(SIGNAL_WINDOW_DAYS))))
                .toList();
        if (activity.isEmpty()) {
            return trending(limit, "Popular with ShopSmart shoppers");
        }

        Instant latestActivity = activity.getFirst().getCreatedAt();
        List<Recommendation> cached = cacheRepository.findByUserIdOrderByScoreDesc(userId);
        if (!cached.isEmpty() && cached.getFirst().getGeneratedAt().isAfter(latestActivity)
                && cached.getFirst().getGeneratedAt().isAfter(clock.instant().minus(CACHE_TTL))) {
            RecommendationResponse fromCache = toResponse(cached.stream()
                    .collect(Collectors.toMap(Recommendation::getProductId, Recommendation::getReason, (a, b) -> a,
                            LinkedHashMap::new)), cached.getFirst().getAlgorithm(), limit);
            if (fromCache.items().size() >= Math.min(limit, 4)) {
                return fromCache;
            }
        }

        Set<Long> exclude = new LinkedHashSet<>();
        exclude.addAll(orderRepository.findPurchasedProductIds(userId));
        activity.stream().filter(a -> a.getEventType() == ActivityType.PURCHASE && a.getProductId() != null)
                .forEach(a -> exclude.add(a.getProductId()));
        cartRepository.findWithItemsByUserId(userId).ifPresent(cart -> cart.getItems()
                .forEach(item -> exclude.add(item.getVariant().getProduct().getId())));

        List<Map<String, Object>> events = activity.stream()
                .filter(a -> a.getProductId() != null)
                .map(a -> Map.<String, Object>of("product_id", a.getProductId(), "event_type", a.getEventType().name(),
                        "weight", a.getEventType().weight(), "occurred_at", a.getCreatedAt().toString()))
                .toList();
        List<String> queries = searchHistoryRepository.recentQueries(userId, PageRequest.of(0, 10));

        Map<Long, String> ranked = new LinkedHashMap<>();
        String strategy;
        Optional<List<AiServiceClient.Scored>> ai = aiClient.forUser(events, queries, List.copyOf(exclude),
                limit * 2);
        if (ai.isPresent() && !ai.get().isEmpty()) {
            strategy = "personalized";
            ai.get().forEach(s -> ranked.put(s.productId(), s.reason() == null ? "Recommended for you" : s.reason()));
        } else {
            strategy = "affinity";
            ranked.putAll(affinityFallback(activity, exclude, limit * 2));
        }
        if (ranked.size() < limit) {
            trendingQuery.trending(clock.instant().minus(TRENDING_WINDOW), limit, List.copyOf(exclude))
                    .forEach(id -> ranked.putIfAbsent(id, "Trending right now"));
        }

        cacheRepository.deleteByUserId(userId);
        Instant now = clock.instant();
        int position = 0;
        for (Map.Entry<Long, String> entry : ranked.entrySet()) {
            Recommendation row = new Recommendation();
            row.setUserId(userId);
            row.setProductId(entry.getKey());
            row.setScore(ranked.size() - position++);
            row.setReason(entry.getValue().length() > 255 ? entry.getValue().substring(0, 255) : entry.getValue());
            row.setAlgorithm(strategy);
            row.setGeneratedAt(now);
            cacheRepository.save(row);
        }
        return toResponse(ranked, strategy, limit);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse similar(Long productId, int limit) {
        Optional<List<AiServiceClient.Scored>> ai = aiClient.similar(productId, limit * 2, List.of(productId));
        if (ai.isPresent() && !ai.get().isEmpty()) {
            Map<Long, String> ranked = new LinkedHashMap<>();
            ai.get().forEach(s -> ranked.put(s.productId(), s.reason() == null ? "Similar product" : s.reason()));
            ranked.remove(productId);
            RecommendationResponse response = toResponse(ranked, "semantic", limit);
            if (!response.items().isEmpty()) {
                return response;
            }
        }
        return new RecommendationResponse("related", productService.related(productId, limit).stream()
                .map(card -> new RecommendedProduct(card, "Similar to what you are viewing"))
                .toList());
    }

    @Transactional(readOnly = true)
    public RecommendationResponse trending(int limit) {
        return trending(limit, "Trending right now");
    }

    private RecommendationResponse trending(int limit, String reason) {
        List<Long> ids = trendingQuery.trending(clock.instant().minus(TRENDING_WINDOW), limit, List.of());
        Map<Long, String> ranked = new LinkedHashMap<>();
        ids.forEach(id -> ranked.put(id, reason));
        return toResponse(ranked, "trending", limit);
    }

    /**
     * Backend-only fallback: scores products by the user's weighted, time-decayed affinity to
     * categories and brands, breaking ties by rating and popularity.
     */
    private Map<Long, String> affinityFallback(List<UserActivity> activity, Set<Long> exclude, int limit) {
        Set<Long> productIds = activity.stream().map(UserActivity::getProductId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Product> interacted = productIds.isEmpty() ? Map.of()
                : productRepository.findAllWithBrandAndCategory(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Double> categoryAffinity = new HashMap<>();
        Map<Long, Double> brandAffinity = new HashMap<>();
        Instant now = clock.instant();
        for (UserActivity event : activity) {
            double decay = Math.pow(0.5, Duration.between(event.getCreatedAt(), now).toDays() / 14.0);
            double weight = event.getEventType().weight() * decay;
            if (event.getCategoryId() != null) {
                categoryAffinity.merge(event.getCategoryId(), weight, Double::sum);
            }
            Product product = event.getProductId() == null ? null : interacted.get(event.getProductId());
            if (product != null) {
                brandAffinity.merge(product.getBrand().getId(), weight * 0.5, Double::sum);
                if (event.getCategoryId() == null) {
                    categoryAffinity.merge(product.getCategory().getId(), weight, Double::sum);
                }
            }
        }
        List<Long> topCategories = categoryAffinity.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()).limit(3).map(Map.Entry::getKey).toList();
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, String> reasons = new HashMap<>();
        for (Long categoryId : topCategories) {
            ProductSearchCriteria criteria = ProductSearchCriteria.storefrontDefaults()
                    .categoryId(categoryId).inStockOnly(true).excludeIds(List.copyOf(exclude))
                    .sort(ProductSort.POPULARITY).build();
            for (Product product : searchRepository.search(criteria, PageRequest.of(0, limit)).getContent()) {
                double score = categoryAffinity.getOrDefault(categoryId, 0.0)
                        + brandAffinity.getOrDefault(product.getBrand().getId(), 0.0)
                        + product.getAverageRating().doubleValue() * 0.3;
                scores.merge(product.getId(), score, Math::max);
                reasons.putIfAbsent(product.getId(), "Because you browsed " + product.getCategory().getName());
            }
        }
        Map<Long, String> ranked = new LinkedHashMap<>();
        scores.entrySet().stream().sorted(Map.Entry.<Long, Double>comparingByValue().reversed()).limit(limit)
                .forEach(e -> ranked.put(e.getKey(), reasons.get(e.getKey())));
        return ranked;
    }

    private RecommendationResponse toResponse(Map<Long, String> ranked, String strategy, int limit) {
        List<ProductSummaryResponse> cards = productService.cardsFor(new ArrayList<>(ranked.keySet()), true);
        List<RecommendedProduct> items = cards.stream().limit(limit)
                .map(card -> new RecommendedProduct(card, ranked.get(card.id())))
                .toList();
        return new RecommendationResponse(strategy, items);
    }
}

package com.shopsmart.search;

import com.shopsmart.activity.ActivityService;
import com.shopsmart.activity.ActivityType;
import com.shopsmart.ai.AiServiceClient;
import com.shopsmart.brand.Brand;
import com.shopsmart.brand.BrandRepository;
import com.shopsmart.category.Category;
import com.shopsmart.category.CategoryRepository;
import com.shopsmart.common.util.Money;
import com.shopsmart.product.KeywordQuery;
import com.shopsmart.product.Product;
import com.shopsmart.product.ProductRepository;
import com.shopsmart.product.ProductSearchCriteria;
import com.shopsmart.product.ProductSearchRepository;
import com.shopsmart.product.ProductService;
import com.shopsmart.product.ProductSort;
import com.shopsmart.product.dto.ProductSummaryResponse;
import com.shopsmart.search.dto.SearchInterpretation;
import com.shopsmart.search.dto.SearchResultItem;
import com.shopsmart.search.dto.SearchSuggestions;
import com.shopsmart.search.dto.SmartSearchResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Hybrid search: (1) parse the query into structured filters with rules, (2) apply them in the
 * database to get candidates, (3) rank candidates semantically with the AI service's embeddings,
 * (4) blend with feature/use-case matches and popularity. If the AI service is unavailable,
 * ranking falls back to keyword relevance, so search always works.
 */
@Service
public class SmartSearchService {

    static final int CANDIDATE_LIMIT = 400;
    /**
     * Weight kept by products outside a category that was only implied by the wording
     * ("earbuds" suggests audio): they stay in the results, but after in-category matches.
     */
    static final double OUTSIDE_IMPLIED_CATEGORY_FACTOR = 0.7;

    private final ProductSearchRepository searchRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final SearchHistoryRepository historyRepository;
    private final ActivityService activityService;
    private final AiServiceClient aiClient;

    public SmartSearchService(ProductSearchRepository searchRepository, ProductRepository productRepository,
                              ProductService productService, CategoryRepository categoryRepository,
                              BrandRepository brandRepository, SearchHistoryRepository historyRepository,
                              ActivityService activityService, AiServiceClient aiClient) {
        this.searchRepository = searchRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.historyRepository = historyRepository;
        this.activityService = activityService;
        this.aiClient = aiClient;
    }

    /** Explicit filters chosen in the UI; they override what was inferred from the text. */
    public record Filters(String categorySlug, List<String> brandSlugs, BigDecimal minPrice, BigDecimal maxPrice,
                          BigDecimal minRating, boolean inStockOnly, ProductSort sort) {
    }

    @Transactional
    public SmartSearchResponse search(String rawQuery, Filters filters, Long userId, int page, int size) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        List<Category> categories = categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        List<Brand> brands = brandRepository.findByActiveTrueOrderByNameAsc();
        ParsedQuery parsed = SearchQueryParser.parse(query, categories, brands);

        ProductSearchCriteria base = ProductSearchCriteria.storefrontDefaults()
                .categorySlug(filters.categorySlug() != null ? filters.categorySlug()
                        : parsed.categoryStrong() ? parsed.categorySlug() : null)
                .brandSlugs(filters.brandSlugs() != null && !filters.brandSlugs().isEmpty() ? filters.brandSlugs()
                        : parsed.brandSlug() == null ? null : List.of(parsed.brandSlug()))
                .minPrice(filters.minPrice() != null ? filters.minPrice() : parsed.minPrice())
                .maxPrice(filters.maxPrice() != null ? filters.maxPrice() : parsed.maxPrice())
                .minRating(filters.minRating() != null ? filters.minRating() : parsed.minRating())
                .inStockOnly(filters.inStockOnly() || parsed.inStockOnly())
                .sort(ProductSort.POPULARITY)
                .build();

        List<Long> candidates = searchRepository.searchIds(base, CANDIDATE_LIMIT);
        boolean relaxed = false;
        if (candidates.isEmpty() && filters.categorySlug() == null && base.categorySlug() != null) {
            base = base.toBuilder().categorySlug(null).build();
            candidates = searchRepository.searchIds(base, CANDIDATE_LIMIT);
            relaxed = true;
        }

        Map<Long, Product> products = candidates.isEmpty() ? Map.of()
                : productRepository.findAllWithBrandAndCategory(candidates).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        String semanticText = StringUtils.hasText(parsed.semanticText()) ? parsed.semanticText() : query;

        Map<Long, Double> relevance = new HashMap<>();
        String engine = "keyword";
        if (StringUtils.hasText(semanticText) && !candidates.isEmpty()) {
            Optional<AiServiceClient.Ranking> ranking = aiClient.rank(semanticText, candidates, candidates.size());
            if (ranking.isPresent() && !ranking.get().results().isEmpty()) {
                engine = "semantic";
                ranking.get().results().forEach(scored -> relevance.put(scored.productId(), scored.score()));
            }
        }
        List<String> tokens = KeywordQuery.tokens(semanticText);
        int maxSold = products.values().stream().mapToInt(Product::getSoldCount).max().orElse(1);

        List<Scored> scored = new ArrayList<>();
        for (Long id : candidates) {
            Product product = products.get(id);
            if (product == null) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            double keyword = keywordScore(product, tokens);
            double semantic = "semantic".equals(engine) ? relevance.getOrDefault(id, 0.0) : keyword;
            double attributes = attributeScore(product, parsed, reasons);
            double popularity = maxSold == 0 ? 0 : (double) product.getSoldCount() / Math.max(1, maxSold);
            double rating = product.getAverageRating().doubleValue() / 5.0;
            Long impliedCategoryId = parsed.categoryStrong() ? null : parsed.categoryId();
            boolean inImpliedCategory = impliedCategoryId != null && impliedCategoryId.equals(product.getCategory().getId());
            if (inImpliedCategory) {
                attributes += 0.5;
                reasons.add(product.getCategory().getName());
            }
            double score = impliedCategoryAdjusted("semantic".equals(engine)
                    ? 0.6 * semantic + 0.15 * keyword + 0.15 * attributes + 0.05 * popularity + 0.05 * rating
                    : 0.6 * keyword + 0.25 * attributes + 0.1 * popularity + 0.05 * rating,
                    impliedCategoryId, product.getCategory().getId());
            boolean hasQuery = !tokens.isEmpty();
            if (hasQuery && semantic <= 0.05 && keyword == 0 && attributes == 0) {
                continue;
            }
            if (parsed.maxPrice() != null || parsed.minPrice() != null) {
                reasons.add(priceReason(parsed));
            }
            scored.add(new Scored(product, score, reasons.stream().distinct().limit(4).toList()));
        }

        ProductSort sort = filters.sort() == null ? ProductSort.RELEVANCE : filters.sort();
        Comparator<Scored> order = switch (sort) {
            case PRICE_ASC -> Comparator.comparing(s -> s.product().getSalePrice());
            case PRICE_DESC -> Comparator.comparing((Scored s) -> s.product().getSalePrice()).reversed();
            case RATING -> Comparator.comparing((Scored s) -> s.product().getAverageRating()).reversed();
            case NEWEST -> Comparator.comparing((Scored s) -> s.product().getCreatedAt()).reversed();
            case POPULARITY -> Comparator.comparingInt((Scored s) -> s.product().getSoldCount()).reversed();
            case DISCOUNT -> Comparator.comparing((Scored s) -> s.product().getDiscountPercent()).reversed();
            default -> Comparator.comparingDouble(Scored::score).reversed();
        };
        scored.sort(order.thenComparing(s -> s.product().getId()));

        int total = scored.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<Scored> pageItems = scored.subList(from, to);
        Map<Long, ProductSummaryResponse> cards = productService.cardsFor(
                pageItems.stream().map(s -> s.product().getId()).toList(), false).stream()
                .collect(Collectors.toMap(ProductSummaryResponse::id, Function.identity()));
        List<SearchResultItem> items = pageItems.stream()
                .filter(s -> cards.containsKey(s.product().getId()))
                .map(s -> new SearchResultItem(cards.get(s.product().getId()), round(s.score()), s.reasons()))
                .toList();

        record(query, SearchType.SMART, total, userId);
        SearchInterpretation interpretation = new SearchInterpretation(
                base.categorySlug() == null ? (parsed.categoryStrong() ? null : parsed.categoryName())
                        : categoryName(categories, base.categorySlug()),
                base.categorySlug(), parsed.brandName(), base.minPrice(), base.maxPrice(), base.minRating(),
                base.inStockOnly(), parsed.features(), parsed.useCases(), relaxed);
        return new SmartSearchResponse(query, interpretation, engine, items, page, size, total,
                size == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    public SearchSuggestions suggestions(String rawQuery, Long userId) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        List<String> recent = userId == null ? List.of() : historyRepository.recentQueries(userId, PageRequest.of(0, 5));
        if (query.length() < 2) {
            return new SearchSuggestions(List.of(), List.of(), List.of(), recent);
        }
        ProductSearchCriteria criteria = ProductSearchCriteria.storefrontDefaults()
                .keywords(query).sort(ProductSort.RELEVANCE).build();
        List<Long> ids = searchRepository.searchIds(criteria, 6);
        List<SearchSuggestions.Suggestion> products = productService.cardsFor(ids, false).stream()
                .map(card -> new SearchSuggestions.Suggestion(card.id(), card.name(), card.slug(), card.thumbnailUrl()))
                .toList();
        List<SearchSuggestions.Suggestion> categories = categoryRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream().filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(query))
                .limit(3).map(c -> new SearchSuggestions.Suggestion(c.getId(), c.getName(), c.getSlug(), c.getImageUrl()))
                .toList();
        List<SearchSuggestions.Suggestion> brands = brandRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(b -> b.getName().toLowerCase(Locale.ROOT).contains(query))
                .limit(3).map(b -> new SearchSuggestions.Suggestion(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl()))
                .toList();
        return new SearchSuggestions(products, categories, brands, recent);
    }

    @Transactional(readOnly = true)
    public List<String> recent(Long userId) {
        return historyRepository.recentQueries(userId, PageRequest.of(0, 10));
    }

    @Transactional
    public void clearHistory(Long userId) {
        historyRepository.deleteByUserId(userId);
    }

    /** Records every search (anonymous too) and, for signed-in users, a SEARCH signal. */
    @Transactional
    public void record(String query, SearchType type, int resultCount, Long userId) {
        if (!StringUtils.hasText(query)) {
            return;
        }
        String normalized = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        SearchHistory entry = new SearchHistory();
        entry.setUserId(userId);
        entry.setQuery(query.length() > 255 ? query.substring(0, 255) : query);
        entry.setNormalizedQuery(normalized.length() > 255 ? normalized.substring(0, 255) : normalized);
        entry.setSearchType(type);
        entry.setResultCount(resultCount);
        historyRepository.save(entry);
        activityService.record(userId, ActivityType.SEARCH, null, null,
                Map.of("query", entry.getNormalizedQuery(), "results", resultCount));
    }

    /** Fraction of query tokens found in the product's search document, with extra weight for the name. */
    static double keywordScore(Product product, List<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        String name = product.getName().toLowerCase(Locale.ROOT);
        String document = product.getSearchText();
        double hits = 0;
        for (String token : tokens) {
            List<String> alternatives = KeywordQuery.termGroups(token).stream().flatMap(List::stream).toList();
            if (alternatives.stream().anyMatch(name::contains)) {
                hits += 1.0;
            } else if (alternatives.stream().anyMatch(document::contains)) {
                hits += 0.6;
            }
        }
        return hits / tokens.size();
    }

    /**
     * A category implied only by the wording is a preference, not a filter: products outside it keep
     * part of their score, so they rank after in-category matches instead of disappearing.
     */
    static double impliedCategoryAdjusted(double score, Long impliedCategoryId, Long productCategoryId) {
        if (impliedCategoryId == null || impliedCategoryId.equals(productCategoryId)) {
            return score;
        }
        return score * OUTSIDE_IMPLIED_CATEGORY_FACTOR;
    }

    private static double attributeScore(Product product, ParsedQuery parsed, List<String> reasons) {
        Set<String> wanted = new LinkedHashSet<>();
        wanted.addAll(parsed.features());
        wanted.addAll(parsed.useCases());
        if (wanted.isEmpty()) {
            return 0;
        }
        String document = product.getSearchText();
        int hits = 0;
        for (String attribute : wanted) {
            String key = attribute.toLowerCase(Locale.ROOT);
            if (document.contains(key) || product.getTags().contains(key)) {
                hits++;
                reasons.add(capitalize(attribute));
            }
        }
        return (double) hits / wanted.size();
    }

    private static String priceReason(ParsedQuery parsed) {
        if (parsed.minPrice() != null && parsed.maxPrice() != null) {
            return "₹" + Money.of(parsed.minPrice()).toBigInteger() + "-₹" + Money.of(parsed.maxPrice()).toBigInteger();
        }
        return parsed.maxPrice() != null ? "Under ₹" + parsed.maxPrice().toBigInteger()
                : "Above ₹" + parsed.minPrice().toBigInteger();
    }

    private static String categoryName(List<Category> categories, String slug) {
        return categories.stream().filter(c -> c.getSlug().equals(slug)).map(Category::getName).findFirst().orElse(slug);
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private record Scored(Product product, double score, List<String> reasons) {
    }
}

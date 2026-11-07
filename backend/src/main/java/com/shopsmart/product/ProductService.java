package com.shopsmart.product;

import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.util.Money;
import com.shopsmart.inventory.Inventory;
import com.shopsmart.inventory.InventoryRepository;
import com.shopsmart.product.dto.ProductDetailResponse;
import com.shopsmart.product.dto.ProductFacetsResponse;
import com.shopsmart.product.dto.ProductSummaryResponse;
import com.shopsmart.product.dto.VariantOption;
import com.shopsmart.product.dto.VariantResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Storefront (read-only) catalog operations. */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository searchRepository;
    private final ProductCardAssembler cardAssembler;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository, ProductSearchRepository searchRepository,
                          ProductCardAssembler cardAssembler, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.searchRepository = searchRepository;
        this.cardAssembler = cardAssembler;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, Pageable pageable) {
        Page<Product> page = searchRepository.search(criteria, pageable);
        List<ProductSummaryResponse> cards = cardAssembler.toCards(page.getContent());
        return new PageResponse<>(cards, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }

    @Transactional(readOnly = true)
    public ProductFacetsResponse facets(ProductSearchCriteria criteria) {
        ProductSearchRepository.Facets facets = searchRepository.facets(criteria);
        return new ProductFacetsResponse(facets.brands().stream()
                .map(b -> new ProductFacetsResponse.BrandFacet(b.id(), b.name(), b.slug(), b.count()))
                .toList(), facets.minPrice(), facets.maxPrice());
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getById(Long id) {
        return toDetail(productRepository.findWithBrandAndCategory(id)
                .filter(ProductService::isStorefrontVisible)
                .orElseThrow(() -> ApiException.notFound("Product")));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getBySlug(String slug) {
        return toDetail(productRepository.findWithBrandAndCategoryBySlug(slug)
                .filter(ProductService::isStorefrontVisible)
                .orElseThrow(() -> ApiException.notFound("Product")));
    }

    /** Products in the same category (then same brand), preferring those in stock. */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> related(Long productId, int limit) {
        Product product = productRepository.findWithBrandAndCategory(productId)
                .filter(ProductService::isStorefrontVisible)
                .orElseThrow(() -> ApiException.notFound("Product"));
        ProductSearchCriteria sameCategory = ProductSearchCriteria.storefrontDefaults()
                .categoryId(product.getCategory().getId())
                .excludeIds(List.of(productId))
                .inStockOnly(true)
                .build();
        List<Product> related = new ArrayList<>(searchRepository.search(sameCategory, PageRequest.of(0, limit))
                .getContent());
        if (related.size() < limit) {
            List<Long> exclude = new ArrayList<>(related.stream().map(Product::getId).toList());
            exclude.add(productId);
            ProductSearchCriteria sameBrand = ProductSearchCriteria.storefrontDefaults()
                    .brandId(product.getBrand().getId())
                    .excludeIds(exclude)
                    .inStockOnly(true)
                    .build();
            related.addAll(searchRepository.search(sameBrand, PageRequest.of(0, limit - related.size()))
                    .getContent());
        }
        return cardAssembler.toCards(related);
    }

    /** Product cards for arbitrary ids, returned in the given order and restricted to visible, in-stock items. */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> cardsFor(List<Long> ids, boolean inStockOnly) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productRepository.findAllWithBrandAndCategory(ids).stream()
                .filter(ProductService::isStorefrontVisible)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> ordered = ids.stream().distinct().map(byId::get).filter(java.util.Objects::nonNull).toList();
        List<ProductSummaryResponse> cards = cardAssembler.toCards(ordered);
        return inStockOnly ? cards.stream().filter(ProductSummaryResponse::inStock).toList() : cards;
    }

    public static boolean isStorefrontVisible(Product product) {
        return product.getStatus() == ProductStatus.ACTIVE && product.getCategory().isActive()
                && product.getBrand().isActive();
    }

    private ProductDetailResponse toDetail(Product product) {
        List<Inventory> stock = inventoryRepository.findActiveByProductIds(List.of(product.getId())).stream()
                .sorted(Comparator.comparingInt((Inventory i) -> i.getVariant().getSortOrder())
                        .thenComparing(i -> i.getVariant().getId()))
                .toList();
        List<VariantResponse> variants = new ArrayList<>();
        int totalAvailable = 0;
        int totalThreshold = 0;
        for (Inventory inventory : stock) {
            ProductVariant variant = inventory.getVariant();
            int available = Math.max(0, inventory.available());
            totalAvailable += available;
            totalThreshold += inventory.getLowStockThreshold();
            StockStatus status = StockStatus.of(available, inventory.getLowStockThreshold());
            variants.add(new VariantResponse(variant.getId(), variant.getSku(), variant.getName(),
                    Map.copyOf(variant.getAttributes()), Money.of(variant.listPrice()),
                    Money.applyDiscount(variant.listPrice(), product.getDiscountPercent()), available > 0, status,
                    status == StockStatus.LOW_STOCK ? available : null));
        }
        VariantResponse cheapest = variants.stream().min(Comparator.comparing(VariantResponse::salePrice)).orElse(null);
        Long defaultVariantId = variants.stream().filter(VariantResponse::inStock).map(VariantResponse::id)
                .findFirst().orElse(variants.isEmpty() ? null : variants.getFirst().id());
        StockStatus productStatus = StockStatus.of(totalAvailable, totalThreshold);
        return new ProductDetailResponse(product.getId(), product.getName(), product.getSlug(),
                ProductMapper.brandRef(product.getBrand()), ProductMapper.categoryRef(product.getCategory()),
                product.getShortDescription(), product.getDescription(),
                cheapest != null ? cheapest.price() : Money.of(product.getPrice()),
                cheapest != null ? cheapest.salePrice() : Money.of(product.getSalePrice()),
                product.getDiscountPercent(), product.getAverageRating(), product.getReviewCount(),
                product.getSoldCount(), product.isFeatured(), totalAvailable > 0, productStatus,
                List.copyOf(product.getTags()),
                product.getSpecifications().stream().map(ProductMapper::specification).toList(),
                product.getImages().stream().map(ProductMapper::image).toList(),
                variants, options(stock), defaultVariantId, product.getCreatedAt());
    }

    private static List<VariantOption> options(List<Inventory> stock) {
        Map<String, Set<String>> axes = new LinkedHashMap<>();
        for (Inventory inventory : stock) {
            inventory.getVariant().getAttributes().forEach((name, value) ->
                    axes.computeIfAbsent(name, key -> new LinkedHashSet<>()).add(value));
        }
        return axes.entrySet().stream()
                .map(entry -> new VariantOption(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }
}

package com.shopsmart.product;

import com.shopsmart.common.util.Money;
import com.shopsmart.inventory.Inventory;
import com.shopsmart.inventory.InventoryRepository;
import com.shopsmart.product.dto.ProductSummaryResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds product cards for any list of products with a fixed number of batched queries
 * (images + inventory), regardless of list size: no N+1 queries.
 * Products must have brand and category loaded.
 */
@Component
public class ProductCardAssembler {

    private final ProductImageRepository imageRepository;
    private final InventoryRepository inventoryRepository;

    public ProductCardAssembler(ProductImageRepository imageRepository, InventoryRepository inventoryRepository) {
        this.imageRepository = imageRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> toCards(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, ProductImage> primaryImages = new HashMap<>();
        for (ProductImage image : imageRepository.findByProductIds(ids)) {
            primaryImages.putIfAbsent(image.getProduct().getId(), image);
        }
        Map<Long, List<Inventory>> stockByProduct = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findActiveByProductIds(ids)) {
            stockByProduct.computeIfAbsent(inventory.getVariant().getProduct().getId(), key -> new ArrayList<>())
                    .add(inventory);
        }
        List<ProductSummaryResponse> cards = new ArrayList<>(products.size());
        for (Product product : products) {
            cards.add(toCard(product, primaryImages.get(product.getId()),
                    stockByProduct.getOrDefault(product.getId(), List.of())));
        }
        return cards;
    }

    public Map<Long, ProductSummaryResponse> toCardMap(List<Product> products) {
        Map<Long, ProductSummaryResponse> map = new LinkedHashMap<>();
        toCards(products).forEach(card -> map.put(card.id(), card));
        return map;
    }

    private ProductSummaryResponse toCard(Product product, ProductImage image, List<Inventory> stock) {
        List<Inventory> ordered = stock.stream()
                .sorted(Comparator.comparingInt((Inventory i) -> i.getVariant().getSortOrder())
                        .thenComparing(i -> i.getVariant().getId()))
                .toList();
        int available = ordered.stream().mapToInt(i -> Math.max(0, i.available())).sum();
        int threshold = ordered.stream().mapToInt(Inventory::getLowStockThreshold).sum();
        StockStatus status = StockStatus.of(available, threshold);

        BigDecimal listPrice = product.getPrice();
        BigDecimal salePrice = product.getSalePrice();
        Inventory cheapest = ordered.stream()
                .min(Comparator.comparing(i -> Money.applyDiscount(i.getVariant().listPrice(),
                        product.getDiscountPercent())))
                .orElse(null);
        if (cheapest != null) {
            listPrice = cheapest.getVariant().listPrice();
            salePrice = Money.applyDiscount(listPrice, product.getDiscountPercent());
        }
        Long defaultVariantId = ordered.stream()
                .filter(i -> i.available() > 0)
                .map(i -> i.getVariant().getId())
                .findFirst()
                .orElse(ordered.isEmpty() ? null : ordered.getFirst().getVariant().getId());
        return new ProductSummaryResponse(product.getId(), product.getName(), product.getSlug(),
                ProductMapper.brandRef(product.getBrand()), ProductMapper.categoryRef(product.getCategory()),
                image == null ? null : image.getUrl(),
                image == null ? null : (image.getThumbnailUrl() != null ? image.getThumbnailUrl() : image.getUrl()),
                Money.of(listPrice), Money.of(salePrice), product.getDiscountPercent(), product.getAverageRating(),
                product.getReviewCount(), available > 0, status,
                status == StockStatus.LOW_STOCK ? available : null, product.isFeatured(), defaultVariantId,
                ordered.size());
    }
}

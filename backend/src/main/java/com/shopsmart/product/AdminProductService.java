package com.shopsmart.product;

import com.shopsmart.admin.AuditService;
import com.shopsmart.admin.StoreSettingsService;
import com.shopsmart.brand.Brand;
import com.shopsmart.brand.BrandService;
import com.shopsmart.category.Category;
import com.shopsmart.category.CategoryService;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.util.Money;
import com.shopsmart.common.util.Slugs;
import com.shopsmart.inventory.Inventory;
import com.shopsmart.inventory.InventoryRepository;
import com.shopsmart.inventory.InventoryService;
import com.shopsmart.product.dto.AdminProductResponse;
import com.shopsmart.product.dto.AdminProductSummary;
import com.shopsmart.product.dto.AdminVariantResponse;
import com.shopsmart.product.dto.ProductUpsertRequest;
import com.shopsmart.product.dto.SpecificationDto;
import com.shopsmart.product.dto.VariantUpsertRequest;
import com.shopsmart.product.events.CatalogNameChangedEvent;
import com.shopsmart.product.events.ProductCatalogChangedEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Product administration: create/update with variants, status changes and soft deletion. */
@Service
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductSearchRepository searchRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final BrandService brandService;
    private final CategoryService categoryService;
    private final StoreSettingsService settingsService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public AdminProductService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                               ProductSearchRepository searchRepository, InventoryRepository inventoryRepository,
                               InventoryService inventoryService, BrandService brandService,
                               CategoryService categoryService, StoreSettingsService settingsService,
                               AuditService auditService, ApplicationEventPublisher events) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.searchRepository = searchRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.brandService = brandService;
        this.categoryService = categoryService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProductSummary> list(ProductSearchCriteria criteria, Pageable pageable) {
        Page<Product> page = searchRepository.search(criteria, pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, List<Inventory>> stock = new HashMap<>();
        if (!ids.isEmpty()) {
            inventoryRepository.findActiveByProductIds(ids).forEach(inventory -> stock
                    .computeIfAbsent(inventory.getVariant().getProduct().getId(), key -> new ArrayList<>())
                    .add(inventory));
        }
        return PageResponse.from(page, product -> {
            List<Inventory> inventories = stock.getOrDefault(product.getId(), List.of());
            int available = inventories.stream().mapToInt(i -> Math.max(0, i.available())).sum();
            int threshold = inventories.stream().mapToInt(Inventory::getLowStockThreshold).sum();
            String image = product.getImages().isEmpty() ? null : product.getImages().getFirst().getThumbnailUrl();
            return new AdminProductSummary(product.getId(), product.getName(), product.getSlug(), image,
                    ProductMapper.brandRef(product.getBrand()), ProductMapper.categoryRef(product.getCategory()),
                    product.getStatus(), product.isFeatured(), product.getPrice(), product.getSalePrice(),
                    product.getDiscountPercent(), available, StockStatus.of(available, threshold),
                    inventories.size(), product.getSoldCount(), product.getAverageRating(), product.getUpdatedAt());
        });
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional
    public AdminProductResponse create(ProductUpsertRequest request) {
        validateVariantSkus(request, null);
        Product product = new Product();
        applyScalars(product, request);
        product.setSlug(Slugs.unique(StringUtils.hasText(request.slug()) ? request.slug()
                : Slugs.slugify(request.name()), productRepository::existsBySlug));
        List<VariantAndStock> created = new ArrayList<>();
        int position = 0;
        for (VariantUpsertRequest variantRequest : request.variants()) {
            ProductVariant variant = new ProductVariant();
            applyVariant(variant, variantRequest, position++);
            product.addVariant(variant);
            created.add(new VariantAndStock(variant, variantRequest));
        }
        requireSellableIfActive(product);
        ProductDerivedFields.refresh(product);
        productRepository.save(product);
        int defaultThreshold = settingsService.current().getDefaultLowStockThreshold();
        for (VariantAndStock entry : created) {
            inventoryService.createForVariant(entry.variant(), valueOr(entry.request().initialStock(), 0),
                    valueOr(entry.request().lowStockThreshold(), defaultThreshold));
        }
        auditService.record("PRODUCT_CREATED", "PRODUCT", product.getId(),
                Map.of("name", product.getName(), "status", product.getStatus().name()));
        events.publishEvent(new ProductCatalogChangedEvent(product.getId()));
        return toResponse(product);
    }

    @Transactional
    public AdminProductResponse update(Long id, ProductUpsertRequest request) {
        Product product = load(id);
        validateVariantSkus(request, id);
        applyScalars(product, request);
        if (StringUtils.hasText(request.slug()) && !request.slug().equals(product.getSlug())) {
            product.setSlug(Slugs.unique(request.slug(), productRepository::existsBySlug));
        }

        Map<Long, ProductVariant> existing = product.getVariants().stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Set<Long> kept = new HashSet<>();
        List<VariantAndStock> added = new ArrayList<>();
        int position = 0;
        for (VariantUpsertRequest variantRequest : request.variants()) {
            if (variantRequest.id() != null) {
                ProductVariant variant = existing.get(variantRequest.id());
                if (variant == null) {
                    throw ApiException.badRequest("Variant " + variantRequest.id() + " does not belong to this product");
                }
                applyVariant(variant, variantRequest, position++);
                kept.add(variant.getId());
            } else {
                ProductVariant variant = new ProductVariant();
                applyVariant(variant, variantRequest, position++);
                product.addVariant(variant);
                added.add(new VariantAndStock(variant, variantRequest));
            }
        }
        // Variants omitted from the request are deactivated, never deleted: orders may reference them.
        existing.values().stream().filter(variant -> !kept.contains(variant.getId()))
                .forEach(variant -> variant.setActive(false));
        requireSellableIfActive(product);
        ProductDerivedFields.refresh(product);
        productRepository.flush();

        int defaultThreshold = settingsService.current().getDefaultLowStockThreshold();
        for (VariantAndStock entry : added) {
            inventoryService.createForVariant(entry.variant(), valueOr(entry.request().initialStock(), 0),
                    valueOr(entry.request().lowStockThreshold(), defaultThreshold));
        }
        for (VariantUpsertRequest variantRequest : request.variants()) {
            if (variantRequest.id() != null && variantRequest.lowStockThreshold() != null) {
                inventoryService.updateThreshold(variantRequest.id(), variantRequest.lowStockThreshold());
            }
        }
        auditService.record("PRODUCT_UPDATED", "PRODUCT", product.getId(),
                Map.of("name", product.getName(), "status", product.getStatus().name()));
        events.publishEvent(new ProductCatalogChangedEvent(product.getId()));
        return toResponse(product);
    }

    @Transactional
    public AdminProductResponse changeStatus(Long id, ProductStatus status) {
        Product product = load(id);
        ProductStatus previous = product.getStatus();
        product.setStatus(status);
        requireSellableIfActive(product);
        auditService.record("PRODUCT_STATUS_CHANGED", "PRODUCT", id,
                Map.of("from", previous.name(), "to", status.name()));
        events.publishEvent(new ProductCatalogChangedEvent(id));
        return toResponse(product);
    }

    /** Soft delete: archived products disappear from the storefront but remain for order history. */
    @Transactional
    public void archive(Long id) {
        Product product = load(id);
        ProductStatus previous = product.getStatus();
        product.setStatus(ProductStatus.ARCHIVED);
        product.setFeatured(false);
        auditService.record("PRODUCT_ARCHIVED", "PRODUCT", id,
                Map.of("name", product.getName(), "previousStatus", previous.name()));
        events.publishEvent(new ProductCatalogChangedEvent(id));
    }

    /** Keeps search documents in sync when a brand or category is renamed. */
    @EventListener
    @Transactional
    public void onCatalogNameChanged(CatalogNameChangedEvent event) {
        List<Product> affected = event.brandId() != null
                ? productRepository.findByBrandId(event.brandId())
                : productRepository.findByCategoryId(event.categoryId());
        affected.forEach(ProductDerivedFields::refresh);
    }

    Product load(Long id) {
        return productRepository.findWithBrandAndCategory(id).orElseThrow(() -> ApiException.notFound("Product"));
    }

    private void applyScalars(Product product, ProductUpsertRequest request) {
        Brand brand = brandService.load(request.brandId());
        Category category = categoryService.load(request.categoryId());
        product.setName(request.name().trim());
        product.setBrand(brand);
        product.setCategory(category);
        product.setShortDescription(StringUtils.hasText(request.shortDescription())
                ? request.shortDescription().trim() : null);
        product.setDescription(request.description().trim());
        product.setPrice(Money.of(request.price()));
        product.setDiscountPercent(request.discountPercent());
        product.setStatus(request.status());
        product.setFeatured(request.featured());

        Set<String> tags = new LinkedHashSet<>();
        if (request.tags() != null) {
            request.tags().stream().map(tag -> tag.trim().toLowerCase(Locale.ROOT)).filter(StringUtils::hasText)
                    .forEach(tags::add);
        }
        product.getTags().clear();
        product.getTags().addAll(tags);

        product.getSpecifications().clear();
        if (request.specifications() != null) {
            for (SpecificationDto spec : request.specifications()) {
                product.getSpecifications().add(new ProductSpecification(spec.name().trim(), spec.value().trim()));
            }
        }
    }

    private static void applyVariant(ProductVariant variant, VariantUpsertRequest request, int position) {
        variant.setSku(request.sku().trim().toUpperCase(Locale.ROOT));
        variant.setName(request.name().trim());
        variant.setPriceOverride(request.priceOverride() == null ? null : Money.of(request.priceOverride()));
        variant.setActive(request.active() == null || request.active());
        variant.setSortOrder(position);
        variant.getAttributes().clear();
        if (request.attributes() != null) {
            request.attributes().forEach((name, value) -> variant.getAttributes().put(name.trim(), value.trim()));
        }
    }

    private void validateVariantSkus(ProductUpsertRequest request, Long productId) {
        List<String> skus = request.variants().stream()
                .map(variant -> variant.sku().trim().toUpperCase(Locale.ROOT))
                .toList();
        Set<String> unique = new HashSet<>(skus);
        if (unique.size() != skus.size()) {
            throw ApiException.badRequest("Each variant must have a different SKU");
        }
        List<String> clashes = productId == null
                ? variantRepository.findExistingSkus(unique)
                : variantRepository.findSkusUsedByOtherProducts(unique, productId);
        if (!clashes.isEmpty()) {
            throw ApiException.duplicate("SKU already in use: " + String.join(", ", clashes));
        }
    }

    private static void requireSellableIfActive(Product product) {
        if (product.getStatus() == ProductStatus.ACTIVE
                && product.getVariants().stream().noneMatch(ProductVariant::isActive)) {
            throw ApiException.businessRule("An active product needs at least one active variant");
        }
    }

    private AdminProductResponse toResponse(Product product) {
        Map<Long, Inventory> stock = new LinkedHashMap<>();
        List<Long> variantIds = product.getVariants().stream().map(ProductVariant::getId).filter(Objects::nonNull)
                .toList();
        if (!variantIds.isEmpty()) {
            inventoryRepository.findByVariantIds(variantIds)
                    .forEach(inventory -> stock.put(inventory.getVariant().getId(), inventory));
        }
        List<AdminVariantResponse> variants = product.getVariants().stream().map(variant -> {
            Inventory inventory = stock.get(variant.getId());
            int quantity = inventory == null ? 0 : inventory.getQuantity();
            int reserved = inventory == null ? 0 : inventory.getReserved();
            return new AdminVariantResponse(variant.getId(), variant.getSku(), variant.getName(),
                    Map.copyOf(variant.getAttributes()), variant.getPriceOverride(), Money.of(variant.listPrice()),
                    Money.applyDiscount(variant.listPrice(), product.getDiscountPercent()), variant.isActive(),
                    quantity, reserved, quantity - reserved, inventory == null ? 0 : inventory.getLowStockThreshold(),
                    variant.getId() != null && variantRepository.hasOrders(variant.getId()));
        }).toList();
        return new AdminProductResponse(product.getId(), product.getName(), product.getSlug(),
                ProductMapper.brandRef(product.getBrand()), ProductMapper.categoryRef(product.getCategory()),
                product.getShortDescription(), product.getDescription(), product.getPrice(),
                product.getDiscountPercent(), product.getSalePrice(), product.getStatus(), product.isFeatured(),
                product.getAverageRating(), product.getReviewCount(), product.getSoldCount(),
                List.copyOf(product.getTags()),
                product.getSpecifications().stream().map(ProductMapper::specification).toList(),
                product.getImages().stream().map(ProductMapper::image).toList(), variants, product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private static int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private record VariantAndStock(ProductVariant variant, VariantUpsertRequest request) {
    }
}

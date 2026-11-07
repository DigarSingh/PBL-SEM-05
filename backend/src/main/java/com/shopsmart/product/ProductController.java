package com.shopsmart.product;

import com.shopsmart.activity.ActivityService;
import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.product.dto.ProductDetailResponse;
import com.shopsmart.product.dto.ProductFacetsResponse;
import com.shopsmart.product.dto.ProductSummaryResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products", description = "Storefront catalog: browsing, keyword search, filtering and product details")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ActivityService activityService;

    public ProductController(ProductService productService, ActivityService activityService) {
        this.productService = productService;
        this.activityService = activityService;
    }

    @Operation(summary = "Browse and keyword-search products",
            description = "Keyword search matches name, brand, category, tags, descriptions and SKUs. "
                    + "sort: relevance, newest, price_asc, price_desc, rating, popularity, discount.")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> list(
            @Parameter(description = "Keywords") @RequestParam(required = false) @Size(max = 200) String q,
            @Parameter(description = "Category slug") @RequestParam(required = false) String category,
            @Parameter(description = "Brand slugs (repeat or comma-separate)") @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("5") BigDecimal minRating,
            @RequestParam(required = false) @DecimalMin("0") @DecimalMax("95") BigDecimal minDiscount,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(60) int size) {
        ProductSearchCriteria criteria = ProductSearchCriteria.storefrontDefaults()
                .keywords(q)
                .categorySlug(blankToNull(category))
                .brandSlugs(brand == null || brand.isEmpty() ? null : brand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .minDiscount(minDiscount)
                .inStockOnly(inStock)
                .featured(featured)
                .sort(ProductSort.parse(sort, q == null || q.isBlank() ? ProductSort.POPULARITY : ProductSort.RELEVANCE))
                .build();
        return ApiResponse.ok(productService.search(criteria, PageRequest.of(page, size)));
    }

    @Operation(summary = "Brand counts and price range for the current filters")
    @GetMapping("/facets")
    public ApiResponse<ProductFacetsResponse> facets(
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "false") boolean inStock) {
        ProductSearchCriteria criteria = ProductSearchCriteria.storefrontDefaults()
                .keywords(q)
                .categorySlug(blankToNull(category))
                .brandSlugs(brand == null || brand.isEmpty() ? null : brand)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .inStockOnly(inStock)
                .build();
        return ApiResponse.ok(productService.facets(criteria));
    }

    @Operation(summary = "Product details by id")
    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    @Operation(summary = "Product details by slug")
    @GetMapping("/slug/{slug}")
    public ApiResponse<ProductDetailResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.ok(productService.getBySlug(slug));
    }

    @Operation(summary = "Related products (same category, then same brand)")
    @GetMapping("/{id}/related")
    public ApiResponse<List<ProductSummaryResponse>> related(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "8") @Min(1) @Max(24) int limit) {
        return ApiResponse.ok(productService.related(id, limit));
    }

    @Operation(summary = "Record a product page view", description = "Called once per product page visit; anonymous views count for popularity.")
    @PostMapping("/{id}/views")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> recordView(@PathVariable Long id, @CurrentUser(required = false) AuthUser user) {
        activityService.recordProductView(id, user == null ? null : user.id());
        return ApiResponse.message("View recorded");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

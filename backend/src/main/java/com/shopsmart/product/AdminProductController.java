package com.shopsmart.product;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.product.dto.AdminProductResponse;
import com.shopsmart.product.dto.AdminProductSummary;
import com.shopsmart.product.dto.ImageAltRequest;
import com.shopsmart.product.dto.ImageOrderRequest;
import com.shopsmart.product.dto.ImageResponse;
import com.shopsmart.product.dto.ImageUrlRequest;
import com.shopsmart.product.dto.ProductUpsertRequest;
import com.shopsmart.product.dto.StatusChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin: Products", description = "Product, variant and image management")
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final AdminProductService productService;
    private final ProductImageService imageService;

    public AdminProductController(AdminProductService productService, ProductImageService imageService) {
        this.productService = productService;
        this.imageService = imageService;
    }

    @Operation(summary = "Search products (all statuses)")
    @GetMapping
    public ApiResponse<PageResponse<AdminProductSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
                .keywords(q)
                .statuses(status == null ? null : Set.of(status))
                .categoryId(categoryId)
                .brandId(brandId)
                .inStockOnly(inStock)
                .sort(ProductSort.parse(sort, ProductSort.NEWEST))
                .build();
        return ApiResponse.ok(productService.list(criteria, PageRequest.of(page, size)));
    }

    @Operation(summary = "Get a product with variants, stock and images")
    @GetMapping("/{id}")
    public ApiResponse<AdminProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(productService.get(id));
    }

    @Operation(summary = "Create a product with its variants and initial stock")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminProductResponse>> create(@Valid @RequestBody ProductUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.create(request), "Product created"));
    }

    @Operation(summary = "Update a product and its variants", description = "Variants missing from the request are deactivated.")
    @PutMapping("/{id}")
    public ApiResponse<AdminProductResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody ProductUpsertRequest request) {
        return ApiResponse.ok(productService.update(id, request), "Product updated");
    }

    @Operation(summary = "Change a product's status")
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminProductResponse> changeStatus(@PathVariable Long id,
                                                          @Valid @RequestBody StatusChangeRequest request) {
        return ApiResponse.ok(productService.changeStatus(id, request.status()), "Status updated");
    }

    @Operation(summary = "Archive (soft-delete) a product")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        productService.archive(id);
        return ApiResponse.message("Product archived");
    }

    @Operation(summary = "Upload product images (JPEG/PNG, max 5 MB each)")
    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<ImageResponse>>> upload(@PathVariable Long id,
                                                                   @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(imageService.upload(id, files), "Images uploaded"));
    }

    @Operation(summary = "Attach an externally hosted image (https)")
    @PostMapping("/{id}/images/url")
    public ResponseEntity<ApiResponse<ImageResponse>> addUrl(@PathVariable Long id,
                                                             @Valid @RequestBody ImageUrlRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(imageService.addExternal(id, request.url(), request.altText()), "Image added"));
    }

    @Operation(summary = "Reorder product images")
    @PutMapping("/{id}/images/order")
    public ApiResponse<List<ImageResponse>> reorder(@PathVariable Long id,
                                                    @Valid @RequestBody ImageOrderRequest request) {
        return ApiResponse.ok(imageService.reorder(id, request.imageIds()), "Image order saved");
    }

    @Operation(summary = "Update an image's alt text")
    @PatchMapping("/{id}/images/{imageId}")
    public ApiResponse<ImageResponse> updateAlt(@PathVariable Long id, @PathVariable Long imageId,
                                                @Valid @RequestBody ImageAltRequest request) {
        return ApiResponse.ok(imageService.updateAltText(id, imageId, request.altText()));
    }

    @Operation(summary = "Delete a product image")
    @DeleteMapping("/{id}/images/{imageId}")
    public ApiResponse<Void> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        imageService.delete(id, imageId);
        return ApiResponse.message("Image deleted");
    }
}

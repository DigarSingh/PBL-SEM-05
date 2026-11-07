package com.shopsmart.brand;

import com.shopsmart.brand.dto.BrandRequest;
import com.shopsmart.brand.dto.BrandResponse;
import com.shopsmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Brands")
@RestController
public class BrandController {

    private final BrandService service;

    public BrandController(BrandService service) {
        this.service = service;
    }

    @Operation(summary = "List brands that have products on sale")
    @GetMapping("/api/brands")
    public ApiResponse<List<BrandResponse>> list() {
        return ApiResponse.ok(service.listActive());
    }

    @Operation(summary = "Admin: list all brands")
    @GetMapping("/api/admin/brands")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<BrandResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @Operation(summary = "Admin: create a brand")
    @PostMapping("/api/admin/brands")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> create(@Valid @RequestBody BrandRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(request), "Brand created"));
    }

    @Operation(summary = "Admin: update a brand")
    @PutMapping("/api/admin/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BrandResponse> update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return ApiResponse.ok(service.update(id, request), "Brand updated");
    }

    @Operation(summary = "Admin: delete a brand without products")
    @DeleteMapping("/api/admin/brands/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.message("Brand deleted");
    }
}

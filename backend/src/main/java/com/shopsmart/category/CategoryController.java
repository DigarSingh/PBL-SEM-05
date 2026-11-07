package com.shopsmart.category;

import com.shopsmart.category.dto.CategoryRequest;
import com.shopsmart.category.dto.CategoryResponse;
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

@Tag(name = "Categories")
@RestController
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @Operation(summary = "List active categories with their product counts")
    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.ok(service.listActive());
    }

    @Operation(summary = "Get an active category by slug")
    @GetMapping("/api/categories/{slug}")
    public ApiResponse<CategoryResponse> get(@PathVariable String slug) {
        return ApiResponse.ok(service.getActiveBySlug(slug));
    }

    @Operation(summary = "Admin: list all categories")
    @GetMapping("/api/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<CategoryResponse>> listAll() {
        return ApiResponse.ok(service.listAll());
    }

    @Operation(summary = "Admin: create a category")
    @PostMapping("/api/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(request), "Category created"));
    }

    @Operation(summary = "Admin: update a category")
    @PutMapping("/api/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(service.update(id, request), "Category updated");
    }

    @Operation(summary = "Admin: delete a category without products")
    @DeleteMapping("/api/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.message("Category deleted");
    }
}

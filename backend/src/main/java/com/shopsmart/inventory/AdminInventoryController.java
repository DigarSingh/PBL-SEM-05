package com.shopsmart.inventory;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.inventory.dto.InventoryItemResponse;
import com.shopsmart.inventory.dto.StockAdjustmentRequest;
import com.shopsmart.inventory.dto.StockMovementResponse;
import com.shopsmart.inventory.dto.ThresholdRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: Inventory", description = "Stock levels, adjustments, thresholds and stock history")
@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "Search stock by product, SKU or category; filter to low / out of stock")
    @GetMapping
    public ApiResponse<PageResponse<InventoryItemResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "false") boolean outOfStock,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(inventoryService.search(q, categoryId, lowStock, outOfStock, page, size));
    }

    @Operation(summary = "Restock or correct a variant's stock")
    @PostMapping("/{variantId}/adjustments")
    public ApiResponse<InventoryItemResponse> adjust(@PathVariable Long variantId,
                                                     @Valid @RequestBody StockAdjustmentRequest request) {
        return ApiResponse.ok(inventoryService.adjust(variantId, request), "Stock updated");
    }

    @Operation(summary = "Change a variant's low-stock threshold")
    @PatchMapping("/{variantId}")
    public ApiResponse<InventoryItemResponse> threshold(@PathVariable Long variantId,
                                                        @Valid @RequestBody ThresholdRequest request) {
        return ApiResponse.ok(inventoryService.updateThreshold(variantId, request.lowStockThreshold()),
                "Threshold updated");
    }

    @Operation(summary = "Stock history of a variant")
    @GetMapping("/{variantId}/movements")
    public ApiResponse<PageResponse<StockMovementResponse>> movements(
            @PathVariable Long variantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(inventoryService.movements(variantId, page, size));
    }
}

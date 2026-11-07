package com.shopsmart.admin;

import com.shopsmart.admin.dto.PublicSettingsResponse;
import com.shopsmart.admin.dto.StoreSettingsRequest;
import com.shopsmart.admin.dto.StoreSettingsResponse;
import com.shopsmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settings", description = "Store configuration (public subset and admin management)")
@RestController
public class SettingsController {

    private final StoreSettingsService service;

    public SettingsController(StoreSettingsService service) {
        this.service = service;
    }

    @Operation(summary = "Public store settings: pricing rules and available payment methods")
    @GetMapping("/api/settings/public")
    public ApiResponse<PublicSettingsResponse> publicSettings() {
        return ApiResponse.ok(service.publicSettings());
    }

    @Operation(summary = "Get store settings")
    @GetMapping("/api/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<StoreSettingsResponse> get() {
        return ApiResponse.ok(service.get());
    }

    @Operation(summary = "Update store settings")
    @PutMapping("/api/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<StoreSettingsResponse> update(@Valid @RequestBody StoreSettingsRequest request) {
        return ApiResponse.ok(service.update(request), "Settings saved");
    }
}

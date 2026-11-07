package com.shopsmart.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * A variant in a product create/update request. {@code id} is null for new variants. Stock is
 * only set on creation ({@code initialStock}); later changes go through inventory adjustments.
 */
public record VariantUpsertRequest(
        Long id,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain letters, digits, '.', '_' and '-'") String sku,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 10) Map<@NotBlank @Size(max = 40) String, @NotBlank @Size(max = 80) String> attributes,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal priceOverride,
        Boolean active,
        @Min(0) @Max(1_000_000) Integer initialStock,
        @Min(0) @Max(100_000) Integer lowStockThreshold) {
}

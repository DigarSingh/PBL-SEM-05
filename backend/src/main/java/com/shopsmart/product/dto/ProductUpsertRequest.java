package com.shopsmart.product.dto;

import com.shopsmart.common.util.Slugs;
import com.shopsmart.product.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ProductUpsertRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 220) @Pattern(regexp = Slugs.PATTERN, message = "must be lowercase words separated by hyphens") String slug,
        @NotNull Long brandId,
        @NotNull Long categoryId,
        @Size(max = 300) String shortDescription,
        @NotBlank @Size(max = 10_000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @DecimalMin("0.00") @DecimalMax("95.00") @Digits(integer = 2, fraction = 2) BigDecimal discountPercent,
        @NotNull ProductStatus status,
        boolean featured,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
        @Size(max = 50) List<@Valid SpecificationDto> specifications,
        @NotEmpty @Size(max = 50) List<@Valid VariantUpsertRequest> variants) {
}

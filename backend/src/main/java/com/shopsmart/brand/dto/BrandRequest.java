package com.shopsmart.brand.dto;

import com.shopsmart.common.util.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 100) @Pattern(regexp = Slugs.PATTERN, message = "must be lowercase words separated by hyphens") String slug,
        @Size(max = 500) String description,
        @Size(max = 1024) @Pattern(regexp = "^(https?://|/).*", message = "must be an http(s) URL") String logoUrl,
        Boolean active) {
}

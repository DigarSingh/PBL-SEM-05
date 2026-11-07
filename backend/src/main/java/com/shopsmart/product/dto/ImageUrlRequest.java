package com.shopsmart.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ImageUrlRequest(
        @NotBlank @Size(max = 1024) @Pattern(regexp = "^https://.+", message = "must be an https URL") String url,
        @Size(max = 255) String altText) {
}

package com.shopsmart.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecificationDto(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 255) String value) {
}

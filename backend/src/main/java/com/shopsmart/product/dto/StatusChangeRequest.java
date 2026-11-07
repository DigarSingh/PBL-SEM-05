package com.shopsmart.product.dto;

import com.shopsmart.product.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull ProductStatus status) {
}

package com.shopsmart.product.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ImageOrderRequest(@NotEmpty List<Long> imageIds) {
}

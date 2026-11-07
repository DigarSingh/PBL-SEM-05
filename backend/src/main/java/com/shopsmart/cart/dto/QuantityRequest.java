package com.shopsmart.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 0 removes the line. */
public record QuantityRequest(@Min(0) @Max(10) int quantity) {
}

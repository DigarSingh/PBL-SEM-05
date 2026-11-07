package com.shopsmart.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ThresholdRequest(@Min(0) @Max(100_000) int lowStockThreshold) {
}

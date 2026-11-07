package com.shopsmart.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CouponCodeRequest(@NotBlank @Size(max = 40) String code) {
}

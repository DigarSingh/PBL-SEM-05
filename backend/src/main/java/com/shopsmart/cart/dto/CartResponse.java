package com.shopsmart.cart.dto;

import com.shopsmart.pricing.PriceQuote;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        PriceQuote summary,
        AppliedCoupon coupon,
        boolean canCheckout,
        List<String> issues) {

    public record AppliedCoupon(String code, boolean valid, String message) {
    }
}

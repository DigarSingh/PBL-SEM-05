package com.shopsmart.order.dto;

/** {@code razorpay} is present when the order must be paid online before it is placed. */
public record CheckoutResponse(OrderDetailResponse order, RazorpayCheckoutResponse razorpay, boolean replayed) {
}

package com.shopsmart.order.dto;

/** Parameters for Razorpay Checkout in the browser. Contains the public key id only, never the secret. */
public record RazorpayCheckoutResponse(
        String keyId,
        String razorpayOrderId,
        long amount,
        String currency,
        String name,
        String description,
        Long orderId,
        String orderNumber,
        Prefill prefill) {

    public record Prefill(String name, String email, String contact) {
    }
}

package com.shopsmart.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Failure details reported by Razorpay Checkout in the browser (informational only). */
public record RazorpayFailureRequest(
        @NotNull Long orderId,
        @Size(max = 64) String razorpayOrderId,
        @Size(max = 64) String razorpayPaymentId,
        @Size(max = 100) String code,
        @Size(max = 255) String description,
        @Size(max = 100) String reason) {
}

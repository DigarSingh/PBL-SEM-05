package com.shopsmart.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RazorpayVerifyRequest(
        @NotNull Long orderId,
        @NotBlank @Size(max = 64) String razorpayOrderId,
        @NotBlank @Size(max = 64) String razorpayPaymentId,
        @NotBlank @Size(max = 128) String razorpaySignature) {
}

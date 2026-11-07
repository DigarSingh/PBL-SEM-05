package com.shopsmart.order.dto;

import com.shopsmart.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Items, prices and the coupon come from the server-side cart; the client only chooses these. */
public record CheckoutRequest(
        @NotNull Long addressId,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 500) String customerNote) {
}

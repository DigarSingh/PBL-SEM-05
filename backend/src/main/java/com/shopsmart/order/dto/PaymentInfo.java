package com.shopsmart.order.dto;

import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentInfo(Long id, PaymentMethod method, PaymentStatus status, BigDecimal amount, String currency,
                          String providerMethod, String razorpayOrderId, String razorpayPaymentId,
                          String failureReason, Instant paidAt, Instant refundedAt, BigDecimal refundedAmount,
                          boolean refundPending) {
}

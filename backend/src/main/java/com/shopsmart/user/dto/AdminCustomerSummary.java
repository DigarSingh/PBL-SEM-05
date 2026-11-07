package com.shopsmart.user.dto;

import com.shopsmart.user.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminCustomerSummary(Long id, String fullName, String email, String phone, UserStatus status,
                                   boolean emailVerified, long orders, BigDecimal totalSpent, Instant lastOrderAt,
                                   Instant createdAt) {
}

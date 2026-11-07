package com.shopsmart.order.dto;

import com.shopsmart.order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(@NotNull OrderStatus status, @Size(max = 500) String note) {
}

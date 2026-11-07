package com.shopsmart.user.dto;

import com.shopsmart.order.dto.OrderSummaryResponse;
import java.util.List;

public record AdminCustomerDetail(AdminCustomerSummary customer, List<AddressResponse> addresses,
                                  List<OrderSummaryResponse> recentOrders, java.time.Instant lastLoginAt) {
}

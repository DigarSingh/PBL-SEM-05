package com.shopsmart.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(Long id, Long productId, Long variantId, String productName, String variantName,
                                String sku, String imageUrl, BigDecimal unitMrp, BigDecimal unitPrice, int quantity,
                                BigDecimal lineTotal) {
}

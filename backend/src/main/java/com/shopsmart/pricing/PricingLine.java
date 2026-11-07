package com.shopsmart.pricing;

import java.math.BigDecimal;

/** A priced line: unit prices come from the catalog, never from the client. */
public record PricingLine(Long variantId, int quantity, BigDecimal unitMrp, BigDecimal unitPrice) {

    public BigDecimal lineMrp() {
        return unitMrp.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

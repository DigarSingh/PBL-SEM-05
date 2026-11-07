package com.shopsmart.pricing;

import com.shopsmart.admin.StoreSettings;
import com.shopsmart.common.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Team Member 1 — Backend Core & Concurrency Lead
 * <p>
 * Server-side dynamic product pricing and tax engine:
 * <ul>
 *   <li>Single source of truth for order arithmetic (cart and checkout)</li>
 *   <li>Dynamic product pricing and coupon validation/deduction</li>
 *   <li>GST tax calculations (both tax-inclusive and tax-exclusive models)</li>
 *   <li>Free shipping threshold evaluation</li>
 * </ul>
 * <p>
 * <ul>
 *   <li>subtotal = sum of catalog selling prices x quantity</li>
 *   <li>shipping is free when (subtotal - coupon) reaches the free-shipping threshold</li>
 *   <li>GST applies to goods and shipping: extracted when prices include tax, added otherwise</li>
 * </ul>
 */
@Service
public class PricingService {

    public PriceQuote quote(List<PricingLine> lines, BigDecimal couponDiscount, StoreSettings settings) {
        int itemCount = lines.stream().mapToInt(PricingLine::quantity).sum();
        BigDecimal mrpTotal = Money.of(lines.stream().map(PricingLine::lineMrp).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal subtotal = Money.of(lines.stream().map(PricingLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal coupon = Money.min(Money.of(couponDiscount), subtotal);
        BigDecimal goods = subtotal.subtract(coupon);

        BigDecimal threshold = Money.of(settings.getFreeShippingThreshold());
        BigDecimal shipping = lines.isEmpty() || goods.compareTo(threshold) >= 0
                ? Money.ZERO : Money.of(settings.getShippingFee());
        BigDecimal toFreeShipping = lines.isEmpty() ? Money.ZERO : Money.max(Money.ZERO, threshold.subtract(goods));

        BigDecimal rate = settings.getTaxRate();
        BigDecimal taxable = goods.add(shipping);
        BigDecimal tax;
        BigDecimal total;
        if (settings.isPricesIncludeTax()) {
            tax = Money.of(taxable.multiply(rate).divide(BigDecimal.ONE.add(rate), 6, RoundingMode.HALF_UP));
            total = Money.of(taxable);
        } else {
            tax = Money.of(taxable.multiply(rate));
            total = Money.of(taxable.add(tax));
        }
        return new PriceQuote(itemCount, mrpTotal, subtotal, Money.of(mrpTotal.subtract(subtotal)), coupon, shipping,
                threshold, toFreeShipping, rate, settings.isPricesIncludeTax(), tax, total, settings.getCurrency());
    }
}

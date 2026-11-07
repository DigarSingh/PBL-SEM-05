package com.shopsmart.product;

import com.shopsmart.common.util.Money;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.StringJoiner;

/** Recomputes the denormalised columns (sale_price, search_text) from a product's current state. */
public final class ProductDerivedFields {

    private static final int MAX_DESCRIPTION_CHARS = 2000;

    private ProductDerivedFields() {
    }

    public static void refresh(Product product) {
        product.setSalePrice(lowestSalePrice(product));
        product.setSearchText(searchDocument(product));
    }

    static BigDecimal lowestSalePrice(Product product) {
        return product.getVariants().stream()
                .filter(ProductVariant::isActive)
                .map(variant -> Money.applyDiscount(variant.listPrice(), product.getDiscountPercent()))
                .min(Comparator.naturalOrder())
                .orElse(Money.applyDiscount(product.getPrice(), product.getDiscountPercent()));
    }

    static String searchDocument(Product product) {
        StringJoiner document = new StringJoiner(" ");
        document.add(product.getName());
        document.add(product.getBrand().getName());
        document.add(product.getCategory().getName());
        product.getTags().forEach(document::add);
        if (product.getShortDescription() != null) {
            document.add(product.getShortDescription());
        }
        for (ProductVariant variant : product.getVariants()) {
            document.add(variant.getName());
            document.add(variant.getSku());
            variant.getAttributes().values().forEach(document::add);
        }
        product.getSpecifications().forEach(spec -> document.add(spec.getName() + " " + spec.getValue()));
        String description = product.getDescription() == null ? "" : product.getDescription();
        document.add(description.length() > MAX_DESCRIPTION_CHARS
                ? description.substring(0, MAX_DESCRIPTION_CHARS) : description);
        return document.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}

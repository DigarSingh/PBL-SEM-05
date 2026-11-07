package com.shopsmart.product.dto;

import java.util.List;

/** An attribute axis for variant pickers, e.g. Colour: [Black, White]. */
public record VariantOption(String name, List<String> values) {
}

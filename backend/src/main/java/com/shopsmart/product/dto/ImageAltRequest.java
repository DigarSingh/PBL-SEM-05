package com.shopsmart.product.dto;

import jakarta.validation.constraints.Size;

public record ImageAltRequest(@Size(max = 255) String altText) {
}

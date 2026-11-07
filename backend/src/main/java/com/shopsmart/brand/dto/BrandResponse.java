package com.shopsmart.brand.dto;

public record BrandResponse(Long id, String name, String slug, String description, String logoUrl, boolean active,
                            long productCount) {
}

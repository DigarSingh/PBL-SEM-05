package com.shopsmart.category.dto;

public record CategoryResponse(Long id, String name, String slug, String description, String imageUrl,
                               int sortOrder, boolean active, long productCount) {
}

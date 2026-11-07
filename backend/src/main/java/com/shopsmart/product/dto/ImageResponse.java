package com.shopsmart.product.dto;

public record ImageResponse(Long id, String url, String thumbnailUrl, String altText, int sortOrder,
                            Integer width, Integer height) {
}

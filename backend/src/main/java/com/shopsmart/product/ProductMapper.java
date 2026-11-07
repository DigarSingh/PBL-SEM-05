package com.shopsmart.product;

import com.shopsmart.brand.Brand;
import com.shopsmart.category.Category;
import com.shopsmart.product.dto.BrandRef;
import com.shopsmart.product.dto.CategoryRef;
import com.shopsmart.product.dto.ImageResponse;
import com.shopsmart.product.dto.SpecificationDto;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static BrandRef brandRef(Brand brand) {
        return new BrandRef(brand.getId(), brand.getName(), brand.getSlug());
    }

    public static CategoryRef categoryRef(Category category) {
        return new CategoryRef(category.getId(), category.getName(), category.getSlug());
    }

    public static ImageResponse image(ProductImage image) {
        return new ImageResponse(image.getId(), image.getUrl(),
                image.getThumbnailUrl() != null ? image.getThumbnailUrl() : image.getUrl(), image.getAltText(),
                image.getSortOrder(), image.getWidth(), image.getHeight());
    }

    public static SpecificationDto specification(ProductSpecification specification) {
        return new SpecificationDto(specification.getName(), specification.getValue());
    }
}

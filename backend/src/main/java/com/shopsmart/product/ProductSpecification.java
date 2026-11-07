package com.shopsmart.product;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A single "name: value" row of a product's specification table. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ProductSpecification {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 255)
    private String value;
}

package com.shopsmart.activity;

import com.shopsmart.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product_views")
public class ProductView extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Null for anonymous visitors. */
    @Column(name = "user_id")
    private Long userId;
}

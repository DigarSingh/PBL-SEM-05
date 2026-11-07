package com.shopsmart.inventory;

import com.shopsmart.common.persistence.BaseEntity;
import com.shopsmart.product.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Immutable history row for every inventory change. */
@Getter
@Setter
@Entity
@Table(name = "stock_movements")
public class StockMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockMovementType type;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "reserved_change", nullable = false)
    private int reservedChange;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(name = "reserved_after", nullable = false)
    private int reservedAfter;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(length = 255)
    private String note;
}

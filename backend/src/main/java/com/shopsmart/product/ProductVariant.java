package com.shopsmart.product;

import com.shopsmart.common.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** A purchasable SKU of a product (e.g. "Black / 256 GB"). Every product has at least one. */
@Getter
@Setter
@Entity
@Table(name = "product_variants")
public class ProductVariant extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    /** Optional list price (MRP) for this variant; the product's price applies when null. */
    @Column(name = "price_override", precision = 12, scale = 2)
    private BigDecimal priceOverride;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ElementCollection
    @CollectionTable(name = "product_variant_attributes", joinColumns = @JoinColumn(name = "variant_id"))
    @MapKeyColumn(name = "name", length = 40)
    @Column(name = "value", nullable = false, length = 80)
    private Map<String, String> attributes = new LinkedHashMap<>();

    /** List price (MRP) of this variant. */
    public BigDecimal listPrice() {
        return priceOverride != null ? priceOverride : product.getPrice();
    }
}

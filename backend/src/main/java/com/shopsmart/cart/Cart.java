package com.shopsmart.cart;

import com.shopsmart.common.persistence.AuditableEntity;
import com.shopsmart.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

/** A customer's persistent cart. Prices are never stored here: they are always recalculated. */
@Getter
@Setter
@Entity
@Table(name = "carts")
public class Cart extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<CartItem> items = new ArrayList<>();

    public Optional<CartItem> findItemByVariantId(Long variantId) {
        return items.stream().filter(item -> item.getVariant().getId().equals(variantId)).findFirst();
    }
}

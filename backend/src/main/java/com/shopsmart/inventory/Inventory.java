package com.shopsmart.inventory;

import com.shopsmart.common.persistence.AuditableEntity;
import com.shopsmart.product.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Stock of one variant. {@code quantity} is on-hand stock not yet sold; {@code reserved} is
 * held for online orders awaiting payment. All mutations go through the domain methods, which
 * enforce the invariants also guarded by database CHECK constraints.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory")
public class Inventory extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Version
    @Column(nullable = false)
    private long version;

    public int available() {
        return quantity - reserved;
    }

    public boolean isLowStock() {
        return available() <= lowStockThreshold;
    }

    /** Sell units immediately (e.g. cash-on-delivery orders). */
    public void deduct(int units) {
        requirePositive(units);
        if (available() < units) {
            throw new IllegalStateException("Insufficient stock");
        }
        quantity -= units;
    }

    /** Hold units for an order awaiting online payment. */
    public void reserve(int units) {
        requirePositive(units);
        if (available() < units) {
            throw new IllegalStateException("Insufficient stock");
        }
        reserved += units;
    }

    /** Convert a reservation into a sale once payment succeeds. */
    public void commitReservation(int units) {
        requirePositive(units);
        if (reserved < units) {
            throw new IllegalStateException("Reservation is smaller than the committed quantity");
        }
        reserved -= units;
        quantity -= units;
    }

    /** Drop a reservation (payment failed or expired). */
    public void releaseReservation(int units) {
        requirePositive(units);
        reserved = Math.max(0, reserved - units);
    }

    /** Return sold units to stock (e.g. order cancelled before shipping). */
    public void restock(int units) {
        requirePositive(units);
        quantity += units;
    }

    /** Manual correction by an administrator; the result may never drop below what is reserved. */
    public void adjust(int delta) {
        int next = quantity + delta;
        if (next < reserved || next < 0) {
            throw new IllegalStateException("Stock cannot go below the reserved quantity (" + reserved + ")");
        }
        quantity = next;
    }

    private static void requirePositive(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Units must be positive");
        }
    }
}

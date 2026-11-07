package com.shopsmart.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** Single-row, admin-editable store configuration (id is always 1). */
@Getter
@Setter
@Entity
@Table(name = "store_settings")
public class StoreSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "store_name", nullable = false, length = 80)
    private String storeName;

    @Column(name = "support_email", nullable = false)
    private String supportEmail;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "free_shipping_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal freeShippingThreshold;

    @Column(name = "default_low_stock_threshold", nullable = false)
    private int defaultLowStockThreshold;

    @Column(name = "cod_enabled", nullable = false)
    private boolean codEnabled;

    @Column(name = "cod_max_order_amount", precision = 12, scale = 2)
    private BigDecimal codMaxOrderAmount;

    @Column(name = "razorpay_enabled", nullable = false)
    private boolean razorpayEnabled;

    @Column(name = "pending_payment_timeout_minutes", nullable = false)
    private int pendingPaymentTimeoutMinutes;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

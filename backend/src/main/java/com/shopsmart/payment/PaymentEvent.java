package com.shopsmart.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A processed webhook delivery; its unique (provider, event_id) key makes processing idempotent. */
@Getter
@Setter
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    public static final String PROVIDER_RAZORPAY = "RAZORPAY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(length = 255)
    private String detail;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}

package com.shopsmart.order;

import com.shopsmart.order.dto.OrderDetailResponse;
import com.shopsmart.order.dto.OrderItemResponse;
import com.shopsmart.order.dto.PaymentInfo;
import com.shopsmart.order.dto.ShippingAddressResponse;
import com.shopsmart.order.dto.StatusHistoryResponse;
import com.shopsmart.order.dto.TimelineStep;
import com.shopsmart.payment.Payment;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentStatus;
import com.shopsmart.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OrderMapper {

    private static final List<OrderStatus> PROGRESS = List.of(OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
    private static final Map<OrderStatus, String> LABELS = Map.of(
            OrderStatus.PENDING_PAYMENT, "Awaiting payment",
            OrderStatus.PLACED, "Order placed",
            OrderStatus.CONFIRMED, "Confirmed",
            OrderStatus.PROCESSING, "Processing",
            OrderStatus.SHIPPED, "Shipped",
            OrderStatus.OUT_FOR_DELIVERY, "Out for delivery",
            OrderStatus.DELIVERED, "Delivered",
            OrderStatus.CANCELLED, "Cancelled");

    private OrderMapper() {
    }

    public static String label(OrderStatus status) {
        return LABELS.get(status);
    }

    public static OrderItemResponse item(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getVariantId(), item.getProductName(),
                item.getVariantName(), item.getSku(), item.getImageUrl(), item.getUnitMrp(), item.getUnitPrice(),
                item.getQuantity(), item.getLineTotal());
    }

    public static PaymentInfo payment(Payment payment, Order order) {
        if (payment == null) {
            return null;
        }
        boolean refundPending = payment.getMethod() == PaymentMethod.RAZORPAY
                && payment.getStatus() == PaymentStatus.PAID && order.getStatus() == OrderStatus.CANCELLED;
        return new PaymentInfo(payment.getId(), payment.getMethod(), payment.getStatus(), payment.getAmount(),
                payment.getCurrency(), payment.getProviderMethod(), payment.getRazorpayOrderId(),
                payment.getRazorpayPaymentId(), payment.getFailureReason(), payment.getPaidAt(),
                payment.getRefundedAt(), payment.getRefundedAmount(), refundPending);
    }

    public static OrderDetailResponse detail(Order order, Payment payment, Map<Long, String> actorNames,
                                             boolean admin, Instant paymentDeadline) {
        ShippingAddress address = order.getShippingAddress();
        List<StatusHistoryResponse> history = order.getStatusHistory().stream()
                .map(entry -> new StatusHistoryResponse(entry.getFromStatus(), entry.getToStatus(), entry.getNote(),
                        admin ? entry.getChangedByUserId() : null,
                        entry.getChangedByUserId() == null ? "System" : actorNames.get(entry.getChangedByUserId()),
                        entry.getCreatedAt()))
                .toList();
        OrderDetailResponse.CustomerInfo customer = null;
        if (admin) {
            User user = order.getUser();
            customer = new OrderDetailResponse.CustomerInfo(user.getId(), user.getFullName(), user.getEmail(),
                    user.getPhone());
        }
        boolean canPay = order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getPaymentMethod() == PaymentMethod.RAZORPAY;
        return new OrderDetailResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getPaymentMethod(), payment(payment, order), order.getItems().stream().map(OrderMapper::item).toList(),
                order.getMrpTotal(), order.getSubtotal(), order.getCouponCode(), order.getCouponDiscount(),
                order.getShippingFee(), order.getTaxRate(), order.isTaxInclusive(), order.getTaxAmount(),
                order.getTotalAmount(), order.getCurrency(),
                new ShippingAddressResponse(address.getFullName(), address.getPhone(), address.getLine1(),
                        address.getLine2(), address.getLandmark(), address.getCity(), address.getState(),
                        address.getPostalCode(), address.getCountry()),
                order.getCustomerNote(), order.getEstimatedDeliveryDate(), order.getCreatedAt(), order.getPlacedAt(),
                order.getDeliveredAt(), order.getCancelledAt(), order.getCancellationReason(), timeline(order),
                history, admin ? order.getStatus().canTransitionTo(OrderStatus.CANCELLED)
                        : order.getStatus().isCancellableByCustomer(),
                canPay, canPay ? paymentDeadline : null, customer,
                admin ? order.getStatus().allowedTransitions() : Set.of());
    }

    /** The fixed delivery pipeline with the time each step was reached (if it was). */
    static List<TimelineStep> timeline(Order order) {
        Map<OrderStatus, Instant> reachedAt = new EnumMap<>(OrderStatus.class);
        for (OrderStatusHistory entry : order.getStatusHistory()) {
            reachedAt.putIfAbsent(entry.getToStatus(), entry.getCreatedAt());
        }
        List<TimelineStep> steps = new ArrayList<>();
        for (OrderStatus status : PROGRESS) {
            steps.add(new TimelineStep(status, LABELS.get(status), reachedAt.containsKey(status), reachedAt.get(status)));
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            steps.add(new TimelineStep(OrderStatus.CANCELLED, LABELS.get(OrderStatus.CANCELLED), true,
                    reachedAt.get(OrderStatus.CANCELLED)));
        }
        return steps;
    }
}

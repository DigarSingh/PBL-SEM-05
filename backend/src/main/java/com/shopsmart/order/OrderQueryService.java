package com.shopsmart.order;

import com.shopsmart.admin.StoreSettingsService;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.order.dto.AdminOrderSummaryResponse;
import com.shopsmart.order.dto.OrderDetailResponse;
import com.shopsmart.order.dto.OrderSummaryResponse;
import com.shopsmart.payment.Payment;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentRepository;
import com.shopsmart.payment.PaymentStatus;
import com.shopsmart.user.User;
import com.shopsmart.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Read models for orders (customer and admin views). */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final StoreSettingsService settingsService;

    public OrderQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                             UserRepository userRepository, StoreSettingsService settingsService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> customerOrders(Long userId, OrderStatus status, int page, int size) {
        Page<Order> orders = orderRepository.findForCustomer(userId, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, Payment> payments = paymentsFor(orders.getContent());
        return PageResponse.from(orders, order -> {
            OrderItem first = order.getItems().isEmpty() ? null : order.getItems().getFirst();
            Payment payment = payments.get(order.getId());
            return new OrderSummaryResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                    order.getPaymentMethod(), payment == null ? null : payment.getStatus(), order.getTotalAmount(),
                    order.totalUnits(), first == null ? null : first.getProductName(),
                    first == null ? null : first.getImageUrl(), order.getCreatedAt(), order.getPlacedAt());
        });
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse customerOrder(Long userId, Long orderId) {
        Order order = orderRepository.findWithUser(orderId)
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Order"));
        return detail(order, false);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse adminOrder(Long orderId) {
        Order order = orderRepository.findWithUser(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        return detail(order, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> adminOrders(OrderStatus status, PaymentStatus paymentStatus,
                                                               PaymentMethod method, boolean includePending,
                                                               String q, Instant from, Instant to, int page, int size) {
        String pattern = StringUtils.hasText(q) ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : null;
        Page<Payment> payments = paymentRepository.searchOrders(paymentStatus, method, status, includePending,
                pattern, from, to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "order.createdAt")));
        return PageResponse.from(payments, payment -> {
            Order order = payment.getOrder();
            User user = order.getUser();
            return new AdminOrderSummaryResponse(order.getId(), order.getOrderNumber(), user.getId(),
                    user.getFullName(), user.getEmail(), order.getStatus(), order.getPaymentMethod(),
                    payment.getStatus(), order.getTotalAmount(), order.totalUnits(), order.getCreatedAt(),
                    order.getPlacedAt());
        });
    }

    /**
     * Customers see their own name for their own actions and "ShopSmart" for staff actions;
     * administrators see every actor's name.
     */
    public OrderDetailResponse detail(Order order, boolean admin) {
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        Long customerId = order.getUser().getId();
        List<Long> actorIds = order.getStatusHistory().stream().map(OrderStatusHistory::getChangedByUserId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> actorNames = new HashMap<>();
        if (admin) {
            userRepository.findAllById(actorIds).forEach(user -> actorNames.put(user.getId(), user.getFullName()));
        } else {
            actorIds.forEach(id -> actorNames.put(id, id.equals(customerId) ? order.getUser().getFullName() : "ShopSmart"));
        }
        Instant deadline = order.getStatus() == OrderStatus.PENDING_PAYMENT
                ? order.getCreatedAt().plus(settingsService.current().getPendingPaymentTimeoutMinutes(),
                ChronoUnit.MINUTES)
                : null;
        return OrderMapper.detail(order, payment, actorNames, admin, deadline);
    }

    private Map<Long, Payment> paymentsFor(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        return paymentRepository.findByOrderIds(orders.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.toMap(payment -> payment.getOrder().getId(), Function.identity()));
    }
}

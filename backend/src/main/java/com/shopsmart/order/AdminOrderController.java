package com.shopsmart.order;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.order.dto.AdminOrderSummaryResponse;
import com.shopsmart.order.dto.CancelOrderRequest;
import com.shopsmart.order.dto.OrderDetailResponse;
import com.shopsmart.order.dto.StatusUpdateRequest;
import com.shopsmart.payment.PaymentMethod;
import com.shopsmart.payment.PaymentStatus;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: Orders", description = "Order management and status updates (pushed to customers in real time)")
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderQueryService queryService;
    private final OrderLifecycleService lifecycle;

    public AdminOrderController(OrderQueryService queryService, OrderLifecycleService lifecycle) {
        this.queryService = queryService;
        this.lifecycle = lifecycle;
    }

    @Operation(summary = "Search orders", description = "Unpaid online orders (PENDING_PAYMENT) are hidden unless includePending=true or filtered explicitly.")
    @GetMapping
    public ApiResponse<PageResponse<AdminOrderSummaryResponse>> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(defaultValue = "false") boolean includePending,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest("'to' must not be before 'from'");
        }
        Instant fromInstant = from == null ? null : from.atStartOfDay(com.shopsmart.common.util.BusinessTime.ZONE).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(com.shopsmart.common.util.BusinessTime.ZONE).toInstant();
        return ApiResponse.ok(queryService.adminOrders(status, paymentStatus, paymentMethod, includePending, q,
                fromInstant, toInstant, page, size));
    }

    @Operation(summary = "Order details with customer, payment and allowed next statuses")
    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(queryService.adminOrder(id));
    }

    @Operation(summary = "Move an order to its next status", description = "Invalid transitions (e.g. DELIVERED -> PROCESSING) are rejected with 409.")
    @PatchMapping("/{id}/status")
    public ApiResponse<OrderDetailResponse> updateStatus(@CurrentUser AuthUser admin, @PathVariable Long id,
                                                         @Valid @RequestBody StatusUpdateRequest request) {
        lifecycle.updateStatus(id, request.status(), request.note(), admin.id());
        return ApiResponse.ok(queryService.adminOrder(id), "Order status updated");
    }

    @Operation(summary = "Cancel an order (restocks items and refunds online payments)")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDetailResponse> cancel(@CurrentUser AuthUser admin, @PathVariable Long id,
                                                   @Valid @RequestBody(required = false) CancelOrderRequest request) {
        lifecycle.updateStatus(id, OrderStatus.CANCELLED, request == null ? null : request.reason(), admin.id());
        return ApiResponse.ok(queryService.adminOrder(id), "Order cancelled");
    }
}

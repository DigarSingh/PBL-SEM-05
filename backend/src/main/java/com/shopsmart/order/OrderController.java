package com.shopsmart.order;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.order.dto.CancelOrderRequest;
import com.shopsmart.order.dto.CheckoutRequest;
import com.shopsmart.order.dto.CheckoutResponse;
import com.shopsmart.order.dto.OrderDetailResponse;
import com.shopsmart.order.dto.OrderSummaryResponse;
import com.shopsmart.order.dto.RazorpayCheckoutResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders", description = "Checkout, order history, tracking and cancellation")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CheckoutFacade checkoutFacade;
    private final OrderQueryService queryService;
    private final OrderLifecycleService lifecycle;

    public OrderController(CheckoutFacade checkoutFacade, OrderQueryService queryService,
                           OrderLifecycleService lifecycle) {
        this.checkoutFacade = checkoutFacade;
        this.queryService = queryService;
        this.lifecycle = lifecycle;
    }

    @Operation(summary = "Place an order from the cart",
            description = "Requires an Idempotency-Key header: retries with the same key return the same order. "
                    + "COD orders are placed immediately; RAZORPAY orders return Razorpay Checkout parameters and are "
                    + "placed once the payment is verified.")
    @PostMapping
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @CurrentUser AuthUser user,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = checkoutFacade.checkout(user.id(), request, idempotencyKey);
        HttpStatus status = response.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        String message = response.razorpay() != null ? "Complete the payment to place your order" : "Order placed";
        return ResponseEntity.status(status).body(ApiResponse.ok(response, message));
    }

    @Operation(summary = "My orders (newest first)")
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(@CurrentUser AuthUser user,
                                                                @RequestParam(required = false) OrderStatus status,
                                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(queryService.customerOrders(user.id(), status, page, size));
    }

    @Operation(summary = "Order details with the tracking timeline")
    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> get(@CurrentUser AuthUser user, @PathVariable Long id) {
        return ApiResponse.ok(queryService.customerOrder(user.id(), id));
    }

    @Operation(summary = "Cancel my order (before it is processed)")
    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDetailResponse> cancel(@CurrentUser AuthUser user, @PathVariable Long id,
                                                   @Valid @RequestBody(required = false) CancelOrderRequest request) {
        lifecycle.cancelByCustomer(user.id(), id, request == null ? null : request.reason());
        return ApiResponse.ok(queryService.customerOrder(user.id(), id), "Order cancelled");
    }

    @Operation(summary = "Start (or retry) the online payment of an unpaid order")
    @PostMapping("/{id}/payment")
    public ApiResponse<RazorpayCheckoutResponse> pay(@CurrentUser AuthUser user, @PathVariable Long id) {
        return ApiResponse.ok(checkoutFacade.payExisting(user.id(), id));
    }
}

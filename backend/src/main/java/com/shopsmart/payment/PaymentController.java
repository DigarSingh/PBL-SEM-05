package com.shopsmart.payment;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.order.OrderQueryService;
import com.shopsmart.order.dto.OrderDetailResponse;
import com.shopsmart.payment.dto.AdminPaymentResponse;
import com.shopsmart.payment.dto.RazorpayFailureRequest;
import com.shopsmart.payment.dto.RazorpayVerifyRequest;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Razorpay verification and webhooks; admin payment management")
@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final RazorpayWebhookService webhookService;
    private final OrderQueryService orderQueryService;

    public PaymentController(PaymentService paymentService, RazorpayWebhookService webhookService,
                             OrderQueryService orderQueryService) {
        this.paymentService = paymentService;
        this.webhookService = webhookService;
        this.orderQueryService = orderQueryService;
    }

    @Operation(summary = "Verify a Razorpay Checkout payment",
            description = "Checks the HMAC signature and confirms the payment with Razorpay before the order is placed.")
    @PostMapping("/api/payments/razorpay/verify")
    public ApiResponse<OrderDetailResponse> verify(@CurrentUser AuthUser user,
                                                   @Valid @RequestBody RazorpayVerifyRequest request) {
        paymentService.verifyRazorpayPayment(user.id(), request);
        return ApiResponse.ok(orderQueryService.customerOrder(user.id(), request.orderId()), "Payment successful");
    }

    @Operation(summary = "Report a failed or abandoned Razorpay Checkout attempt")
    @PostMapping("/api/payments/razorpay/failure")
    public ApiResponse<OrderDetailResponse> failure(@CurrentUser AuthUser user,
                                                    @Valid @RequestBody RazorpayFailureRequest request) {
        paymentService.recordCheckoutFailure(user.id(), request);
        return ApiResponse.ok(orderQueryService.customerOrder(user.id(), request.orderId()),
                "Payment was not completed. You can retry from your order page.");
    }

    @Operation(summary = "Razorpay webhook endpoint", description = "Authenticated by X-Razorpay-Signature; idempotent per X-Razorpay-Event-Id.")
    @PostMapping("/api/payments/razorpay/webhook")
    public ApiResponse<Map<String, String>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        return ApiResponse.ok(Map.of("outcome", webhookService.handle(rawBody, signature, eventId)));
    }

    @Operation(summary = "Admin: search payments")
    @GetMapping("/api/admin/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<AdminPaymentResponse>> search(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(paymentService.search(status, method, q, page, size));
    }

    @Operation(summary = "Admin: mark a Cash on Delivery payment as collected")
    @PostMapping("/api/admin/payments/{id}/collect")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminPaymentResponse> collect(@CurrentUser AuthUser admin, @PathVariable Long id) {
        return ApiResponse.ok(paymentService.collectCod(id, admin.id()), "Payment marked as collected");
    }

    @Operation(summary = "Admin: retry the refund of a cancelled, paid online order")
    @PostMapping("/api/admin/payments/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminPaymentResponse> refund(@PathVariable Long id) {
        return ApiResponse.ok(paymentService.retryRefund(id), "Refund requested");
    }
}

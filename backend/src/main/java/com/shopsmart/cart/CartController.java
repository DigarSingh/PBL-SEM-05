package com.shopsmart.cart;

import com.shopsmart.cart.dto.CartItemRequest;
import com.shopsmart.cart.dto.CartResponse;
import com.shopsmart.cart.dto.CouponCodeRequest;
import com.shopsmart.cart.dto.QuantityRequest;
import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart", description = "Persistent cart with server-side pricing, coupons, tax and shipping")
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Get my cart with a freshly calculated price summary")
    @GetMapping
    public ApiResponse<CartResponse> get(@CurrentUser AuthUser user) {
        return ApiResponse.ok(cartService.get(user.id()));
    }

    @Operation(summary = "Add a product variant to the cart")
    @PostMapping("/items")
    public ApiResponse<CartResponse> add(@CurrentUser AuthUser user, @Valid @RequestBody CartItemRequest request) {
        return ApiResponse.ok(cartService.addItem(user.id(), request.variantId(), request.quantity()), "Added to cart");
    }

    @Operation(summary = "Change the quantity of a cart line (0 removes it)")
    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartResponse> update(@CurrentUser AuthUser user, @PathVariable Long itemId,
                                            @Valid @RequestBody QuantityRequest request) {
        return ApiResponse.ok(cartService.updateQuantity(user.id(), itemId, request.quantity()));
    }

    @Operation(summary = "Remove a cart line")
    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> remove(@CurrentUser AuthUser user, @PathVariable Long itemId) {
        return ApiResponse.ok(cartService.removeItem(user.id(), itemId), "Removed from cart");
    }

    @Operation(summary = "Empty the cart")
    @DeleteMapping
    public ApiResponse<CartResponse> clear(@CurrentUser AuthUser user) {
        return ApiResponse.ok(cartService.clear(user.id()), "Cart cleared");
    }

    @Operation(summary = "Apply a coupon code (validated on the server)")
    @PostMapping("/coupon")
    public ApiResponse<CartResponse> applyCoupon(@CurrentUser AuthUser user,
                                                 @Valid @RequestBody CouponCodeRequest request) {
        return ApiResponse.ok(cartService.applyCoupon(user.id(), request.code()), "Coupon applied");
    }

    @Operation(summary = "Remove the applied coupon")
    @DeleteMapping("/coupon")
    public ApiResponse<CartResponse> removeCoupon(@CurrentUser AuthUser user) {
        return ApiResponse.ok(cartService.removeCoupon(user.id()), "Coupon removed");
    }
}

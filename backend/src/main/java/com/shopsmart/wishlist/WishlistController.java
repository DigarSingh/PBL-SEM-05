package com.shopsmart.wishlist;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import com.shopsmart.wishlist.dto.WishlistItemRequest;
import com.shopsmart.wishlist.dto.WishlistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wishlist")
@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Operation(summary = "Get my wishlist")
    @GetMapping
    public ApiResponse<WishlistResponse> get(@CurrentUser AuthUser user) {
        return ApiResponse.ok(wishlistService.get(user.id()));
    }

    @Operation(summary = "Ids of the products on my wishlist (for heart icons)")
    @GetMapping("/ids")
    public ApiResponse<List<Long>> ids(@CurrentUser AuthUser user) {
        return ApiResponse.ok(wishlistService.productIds(user.id()));
    }

    @Operation(summary = "Save a product to my wishlist")
    @PostMapping("/items")
    public ApiResponse<WishlistResponse> add(@CurrentUser AuthUser user, @Valid @RequestBody WishlistItemRequest request) {
        return ApiResponse.ok(wishlistService.add(user.id(), request.productId()), "Saved to wishlist");
    }

    @Operation(summary = "Remove a product from my wishlist")
    @DeleteMapping("/items/{productId}")
    public ApiResponse<WishlistResponse> remove(@CurrentUser AuthUser user, @PathVariable Long productId) {
        return ApiResponse.ok(wishlistService.remove(user.id(), productId), "Removed from wishlist");
    }

    @Operation(summary = "Move a wishlist product into the cart")
    @PostMapping("/items/{productId}/move-to-cart")
    public ApiResponse<WishlistResponse> moveToCart(@CurrentUser AuthUser user, @PathVariable Long productId) {
        return ApiResponse.ok(wishlistService.moveToCart(user.id(), productId), "Moved to cart");
    }
}

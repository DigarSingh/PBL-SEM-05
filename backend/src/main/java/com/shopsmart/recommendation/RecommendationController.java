package com.shopsmart.recommendation;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.recommendation.dto.RecommendationResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recommendations", description = "Personalised, similar and trending products")
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @Operation(summary = "Recommendations for the current shopper",
            description = "Personalised from views, searches, cart, wishlist and purchases when signed in; trending otherwise.")
    @GetMapping("/for-you")
    public ApiResponse<RecommendationResponse> forYou(@CurrentUser(required = false) AuthUser user,
                                                      @RequestParam(defaultValue = "12") @Min(1) @Max(48) int limit) {
        return ApiResponse.ok(service.forYou(user == null ? null : user.id(), limit));
    }

    @Operation(summary = "Products similar to a product")
    @GetMapping("/similar/{productId}")
    public ApiResponse<RecommendationResponse> similar(@PathVariable Long productId,
                                                       @RequestParam(defaultValue = "8") @Min(1) @Max(24) int limit) {
        return ApiResponse.ok(service.similar(productId, limit));
    }

    @Operation(summary = "Trending products (recent sales, views and wishlist saves)")
    @GetMapping("/trending")
    public ApiResponse<RecommendationResponse> trending(@RequestParam(defaultValue = "12") @Min(1) @Max(48) int limit) {
        return ApiResponse.ok(service.trending(limit));
    }
}

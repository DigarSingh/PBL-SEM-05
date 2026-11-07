package com.shopsmart.search;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.product.ProductSort;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import com.shopsmart.search.dto.SearchSuggestions;
import com.shopsmart.search.dto.SmartSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Smart Search", description = "Natural-language product search, suggestions and recent searches")
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SmartSearchService searchService;

    public SearchController(SmartSearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "Search products using natural language")
    @GetMapping
    public ApiResponse<SmartSearchResponse> search(
            @RequestParam(defaultValue = "") @Size(max = 255) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(60) int size,
            @CurrentUser(required = false) AuthUser user) {
        SmartSearchService.Filters filters = new SmartSearchService.Filters(
                blankToNull(category), normalizeBrands(brand), minPrice, maxPrice, minRating,
                inStock, ProductSort.parse(sort, ProductSort.RELEVANCE));
        Long userId = user == null ? null : user.id();
        return ApiResponse.ok(searchService.search(q, filters, userId, page, size));
    }

    @Operation(summary = "Get search suggestions")
    @GetMapping("/suggestions")
    public ApiResponse<SearchSuggestions> suggestions(
            @RequestParam(defaultValue = "") @Size(max = 255) String q,
            @CurrentUser(required = false) AuthUser user) {
        return ApiResponse.ok(searchService.suggestions(q, user == null ? null : user.id()));
    }

    @Operation(summary = "Get recent searches")
    @GetMapping("/recent")
    public ApiResponse<List<String>> recent(@CurrentUser AuthUser user) {
        return ApiResponse.ok(searchService.recent(user.id()));
    }

    @Operation(summary = "Clear recent searches")
    @DeleteMapping("/recent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> clearRecent(@CurrentUser AuthUser user) {
        searchService.clearHistory(user.id());
        return ApiResponse.message("Recent searches cleared");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> normalizeBrands(List<String> brands) {
        if (brands == null) {
            return null;
        }
        return brands.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}

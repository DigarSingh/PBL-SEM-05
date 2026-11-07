package com.shopsmart.user;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.api.PageResponse;
import com.shopsmart.user.dto.AdminCustomerDetail;
import com.shopsmart.user.dto.AdminCustomerSummary;
import com.shopsmart.user.dto.CustomerStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin: Customers")
@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private final AdminCustomerService service;

    public AdminCustomerController(AdminCustomerService service) {
        this.service = service;
    }

    @Operation(summary = "Search customers with order count and lifetime spend")
    @GetMapping
    public ApiResponse<PageResponse<AdminCustomerSummary>> search(@RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) UserStatus status,
                                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.search(q, status, page, size));
    }

    @Operation(summary = "Customer profile, addresses and recent orders")
    @GetMapping("/{id}")
    public ApiResponse<AdminCustomerDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Enable or disable a customer account")
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminCustomerSummary> changeStatus(@PathVariable Long id,
                                                          @Valid @RequestBody CustomerStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(id, request.status()), "Customer updated");
    }
}

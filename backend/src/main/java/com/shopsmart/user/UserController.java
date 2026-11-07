package com.shopsmart.user;

import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.security.AuthUser;
import com.shopsmart.security.CurrentUser;
import com.shopsmart.user.dto.AddressRequest;
import com.shopsmart.user.dto.AddressResponse;
import com.shopsmart.user.dto.ChangePasswordRequest;
import com.shopsmart.user.dto.UpdateProfileRequest;
import com.shopsmart.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "The signed-in customer's profile, password and addresses")
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    public UserController(UserService userService, AddressService addressService) {
        this.userService = userService;
        this.addressService = addressService;
    }

    @Operation(summary = "Get my profile")
    @GetMapping
    public ApiResponse<UserProfileResponse> me(@CurrentUser AuthUser user) {
        return ApiResponse.ok(userService.getProfile(user.id()));
    }

    @Operation(summary = "Update my profile")
    @PatchMapping
    public ApiResponse<UserProfileResponse> update(@CurrentUser AuthUser user,
                                                   @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(user.id(), request), "Profile updated");
    }

    @Operation(summary = "Change my password", description = "Signs out all other sessions.")
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@CurrentUser AuthUser user,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.id(), request);
        return ApiResponse.message("Password changed. Other sessions have been signed out.");
    }

    @Operation(summary = "List my addresses")
    @GetMapping("/addresses")
    public ApiResponse<List<AddressResponse>> addresses(@CurrentUser AuthUser user) {
        return ApiResponse.ok(addressService.list(user.id()));
    }

    @Operation(summary = "Add an address")
    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@CurrentUser AuthUser user,
                                                                   @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(addressService.create(user.id(), request), "Address saved"));
    }

    @Operation(summary = "Update an address")
    @PutMapping("/addresses/{addressId}")
    public ApiResponse<AddressResponse> updateAddress(@CurrentUser AuthUser user, @PathVariable Long addressId,
                                                      @Valid @RequestBody AddressRequest request) {
        return ApiResponse.ok(addressService.update(user.id(), addressId, request), "Address updated");
    }

    @Operation(summary = "Make an address the default")
    @PostMapping("/addresses/{addressId}/default")
    public ApiResponse<AddressResponse> makeDefault(@CurrentUser AuthUser user, @PathVariable Long addressId) {
        return ApiResponse.ok(addressService.makeDefault(user.id(), addressId), "Default address updated");
    }

    @Operation(summary = "Delete an address")
    @DeleteMapping("/addresses/{addressId}")
    public ApiResponse<Void> deleteAddress(@CurrentUser AuthUser user, @PathVariable Long addressId) {
        addressService.delete(user.id(), addressId);
        return ApiResponse.message("Address deleted");
    }
}

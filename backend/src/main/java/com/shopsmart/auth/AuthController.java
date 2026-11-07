package com.shopsmart.auth;

import com.shopsmart.auth.dto.AuthResponse;
import com.shopsmart.auth.dto.EmailRequest;
import com.shopsmart.auth.dto.LoginRequest;
import com.shopsmart.auth.dto.RegisterRequest;
import com.shopsmart.auth.dto.RegisterResponse;
import com.shopsmart.auth.dto.ResetPasswordRequest;
import com.shopsmart.auth.dto.TokenRequest;
import com.shopsmart.common.api.ApiResponse;
import com.shopsmart.common.exception.ApiException;
import com.shopsmart.common.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Registration, sign-in, token refresh, e-mail verification and password reset")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Cookie-authenticated endpoints require this header, which cross-site forms cannot send. */
    static final String CSRF_GUARD_HEADER = "X-Requested-With";

    private final AuthService authService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, AuthCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @Operation(summary = "Register a customer account", description = "Sends a verification e-mail; sign-in is possible once the address is verified.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        String message = response.emailVerificationRequired()
                ? "Account created. Check your email to verify your address."
                : "Account created. You can now sign in.";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response, message));
    }

    @Operation(summary = "Sign in", description = "Returns a short-lived access token and sets the HttpOnly refresh-token cookie.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request, RequestMetadata.clientIp(),
                RequestMetadata.userAgent());
        return withRefreshCookie(result, "Signed in");
    }

    @Operation(summary = "Refresh the access token", description = "Uses and rotates the refresh-token cookie. Requires the X-Requested-With header.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = CSRF_GUARD_HEADER, required = false) String requestedWith) {
        requireCsrfGuard(requestedWith);
        try {
            AuthService.LoginResult result = authService.refresh(refreshToken, RequestMetadata.clientIp(),
                    RequestMetadata.userAgent());
            return withRefreshCookie(result, null);
        } catch (ApiException e) {
            // Clear a dead cookie so the browser stops sending it.
            return ResponseEntity.status(e.getStatus())
                    .header(HttpHeaders.SET_COOKIE, cookies.clearedRefreshCookie().toString())
                    .body(new ApiResponse<>(false, null, e.getMessage()));
        }
    }

    @Operation(summary = "Sign out", description = "Revokes the refresh-token family and clears the cookie.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = CSRF_GUARD_HEADER, required = false) String requestedWith) {
        requireCsrfGuard(requestedWith);
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clearedRefreshCookie().toString())
                .body(ApiResponse.message("Signed out"));
    }

    @Operation(summary = "Verify an e-mail address with the token from the verification e-mail")
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody TokenRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.message("Email verified. You can now sign in.");
    }

    @Operation(summary = "Resend the verification e-mail")
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody EmailRequest request) {
        authService.resendVerification(request.email());
        return ApiResponse.message("If an unverified account exists for that email, a new link has been sent.");
    }

    @Operation(summary = "Request a password reset e-mail")
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.forgotPassword(request.email());
        return ApiResponse.message("If an account exists for that email, a password reset link has been sent.");
    }

    @Operation(summary = "Set a new password with the token from the reset e-mail")
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.message("Your password has been reset. Please sign in.");
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(AuthService.LoginResult result,
                                                                        String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.refreshCookie(result.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(result.response(), message));
    }

    private static void requireCsrfGuard(String requestedWith) {
        if (requestedWith == null || requestedWith.isBlank()) {
            throw ApiException.forbidden("Missing " + CSRF_GUARD_HEADER + " header");
        }
    }
}

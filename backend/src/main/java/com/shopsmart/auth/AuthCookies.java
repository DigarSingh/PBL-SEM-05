package com.shopsmart.auth;

import com.shopsmart.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the HttpOnly refresh-token cookie, scoped to the auth endpoints only. */
@Component
public class AuthCookies {

    public static final String REFRESH_COOKIE = "ss_refresh";
    static final String COOKIE_PATH = "/api/auth";

    private final AppProperties properties;

    public AuthCookies(AppProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie refreshCookie(String rawToken) {
        return base(rawToken).maxAge(properties.jwt().refreshExpiration()).build();
    }

    public ResponseCookie clearedRefreshCookie() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}

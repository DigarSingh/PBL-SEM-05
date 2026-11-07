package com.shopsmart.security;

import com.shopsmart.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 responses in the standard error format. An invalid/expired bearer token yields
 * {@code INVALID_TOKEN}, which clients use as the signal to refresh their access token.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter writer;

    public RestAuthenticationEntryPoint(SecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean invalidToken = authException instanceof InvalidBearerTokenException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, invalidToken
                ? "Bearer error=\"invalid_token\"" : "Bearer");
        writer.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                invalidToken ? ErrorCode.INVALID_TOKEN : ErrorCode.UNAUTHORIZED,
                invalidToken ? "Your session has expired. Please sign in again." : "Authentication is required");
    }
}
